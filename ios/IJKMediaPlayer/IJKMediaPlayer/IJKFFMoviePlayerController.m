/*
 * IJKFFMoviePlayerController.m
 *
 * Copyright (c) 2013 Bilibili
 * Copyright (c) 2013 Zhang Rui <bbcallen@gmail.com>
 *
 * This file is part of ijkPlayer.
 *
 * ijkPlayer is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * ijkPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with ijkPlayer; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#import "IJKFFMoviePlayerController.h"

#import <UIKit/UIKit.h>
#import "IJKSDLHudViewController.h"
#import "IJKFFMoviePlayerDef.h"
#import "IJKMediaPlayback.h"
#import "IJKMediaModule.h"
#import "IJKAudioKit.h"
#import "IJKNotificationManager.h"
#import "NSString+IJKMedia.h"
#import <WebRTC/WebRTC.h>
#import "ARDAppClient.h"
#import "ARDCaptureController.h"
#import "ARDSettingsModel.h"
#import "Nebula_interface.h"
#include "string.h"

static const char *kIJKFFRequiredFFmpegVersion = "0.5.2";
static const char *kIJKVideoViewVersion = "0.9.35";
static const int MIN_DISTANCE = 5;
static const float TRACKING_SPEED = 0.05f;
static const int TRACKING_THRESHOLD_IN_SECONDS = 3;

// It means you didn't call shutdown if you found this object leaked.
@interface IJKWeakHolder : NSObject
@property (nonatomic, weak) id object;
@end

@implementation IJKWeakHolder
@end

@interface IJKFFMoviePlayerController()

@end

@implementation IJKFFMoviePlayerController {
    IjkMediaPlayer *_mediaPlayer;
    IJKSDLGLView *_glView;
    IJKFFMoviePlayerMessagePool *_msgPool;
    NSString *_urlString;
    
    NSInteger _videoWidth;
    NSInteger _videoHeight;
    NSInteger _sampleAspectRatioNumerator;
    NSInteger _sampleAspectRatioDenominator;
    
    BOOL      _seeking;
    NSInteger _bufferingTime;
    NSInteger _bufferingPosition;
    
    BOOL _keepScreenOnWhilePlaying;
    BOOL _pauseInBackground;
    BOOL _isVideoToolboxOpen;
    BOOL _playingBeforeInterruption;
    
    IJKNotificationManager *_notificationManager;
    
    NSTimer *_hudTimer;
    IJKSDLHudViewController *_hudViewController;
    
    ARDAppClient *_client;
    ARDCaptureController *_captureController;
}

@synthesize view = _view;
@synthesize currentPlaybackTime;
@synthesize currentRecordingTime;
@synthesize duration;
@synthesize playableDuration;
@synthesize bufferingProgress = _bufferingProgress;

@synthesize numberOfBytesTransferred = _numberOfBytesTransferred;

@synthesize isPreparedToPlay = _isPreparedToPlay;
@synthesize playbackState = _playbackState;
@synthesize loadState = _loadState;

@synthesize naturalSize = _naturalSize;
@synthesize scalingMode = _scalingMode;
@synthesize shouldAutoplay = _shouldAutoplay;

@synthesize allowsMediaAirPlay = _allowsMediaAirPlay;
@synthesize airPlayMediaActive = _airPlayMediaActive;

@synthesize isDanmakuMediaAirPlay = _isDanmakuMediaAirPlay;

@synthesize monitor = _monitor;
@synthesize shouldShowHudView           = _shouldShowHudView;
@synthesize asyncShutdown = _asyncShutdown;
@synthesize isSeekBuffering = _isSeekBuffering;
@synthesize isAudioSync = _isAudioSync;
@synthesize isVideoSync = _isVideoSync;

@synthesize RGBAFrame;
@synthesize AudioFrame;
@synthesize currentX = _currentX;
@synthesize currentY = _currentY;
@synthesize lastFoundObjectTime = _lastFoundObjectTime;

#define FFP_IO_STAT_STEP (50 * 1024)

#pragma mark - ARDAppClientDelegate

- (void)appClient:(ARDAppClient *)client
    didChangeState:(ARDAppClientState)state {
  switch (state) {
    case kARDAppClientStateConnected:
      NSLog(@"WebRTC Client connected.");
      break;
    case kARDAppClientStateConnecting:
      NSLog(@"WebRTC Client connecting.");
      break;
    case kARDAppClientStateDisconnected:
      NSLog(@"WebRTC Client disconnected.");
      break;
  }
}

- (void)appClient:(ARDAppClient *)client
    didChangeConnectionState:(RTCIceConnectionState)state {
    NSLog(@"ICE state changed: %ld", (long)state);
    if (state == RTCIceConnectionStateDisconnected) {
        if (!self.inShutdown) {
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerPlaybackDidFinishNotification
             object:self
             userInfo:@{
                        IJKMPMoviePlayerPlaybackDidFinishReasonUserInfoKey: @(IJKMPMovieFinishReasonPlaybackError),
                        @"error": @(0)}];
            [self shutdown];
        }
    }
}

- (void)appClient:(ARDAppClient *)client
    didCreateLocalCapturer:(RTCCameraVideoCapturer *)localCapturer {
    ARDSettingsModel *settingsModel = [[ARDSettingsModel alloc] init];
    _captureController = [[ARDCaptureController alloc] initWithCapturer:localCapturer settings:settingsModel];
    [_captureController startCapture];
}

- (void)appClient:(ARDAppClient *)client
    didCreateLocalFileCapturer:(RTCFileVideoCapturer *)fileCapturer {
}

- (void)appClient:(ARDAppClient *)client
    didReceiveLocalVideoTrack:(RTCVideoTrack *)localVideoTrack {
}

- (void)appClient:(ARDAppClient *)client
    didReceiveRemoteVideoTrack:(RTCVideoTrack *)remoteVideoTrack {
}

- (void)appClient:(ARDAppClient *)client
      didGetStats:(NSArray *)stats {
}

- (void)appClient:(ARDAppClient *)client
         didError:(NSError *)error {
    NSString *message = [NSString stringWithFormat:@"%@", error.localizedDescription];
    NSLog(@"didError %@", message);
}

#pragma mark - RTCVideoViewDelegate

- (void)videoView:(id<RTCVideoRenderer>)videoView didChangeVideoSize:(CGSize)size {
}

#pragma mark - RTCAudioSessionDelegate

- (void)audioSession:(RTCAudioSession *)audioSession
    didDetectPlayoutGlitch:(int64_t)totalNumberOfGlitches {
}

- (void)stopWebRTC {
    if (_captureController != nil) {
        [_captureController stopCapture];
        _captureController = nil;
    }
    [_client disconnect];
}

// as an example
void IJKFFIOStatDebugCallback(const char *url, int type, int bytes)
{
    static int64_t s_ff_io_stat_check_points = 0;
    static int64_t s_ff_io_stat_bytes = 0;
    if (!url)
        return;
    
    if (type != IJKMP_IO_STAT_READ)
        return;
    
    if (!av_strstart(url, "http:", NULL))
        return;
    
    s_ff_io_stat_bytes += bytes;
    if (s_ff_io_stat_bytes < s_ff_io_stat_check_points ||
        s_ff_io_stat_bytes > s_ff_io_stat_check_points + FFP_IO_STAT_STEP) {
        s_ff_io_stat_check_points = s_ff_io_stat_bytes;
        NSLog(@"io-stat: %s, +%d = %"PRId64"\n", url, bytes, s_ff_io_stat_bytes);
    }
}

void IJKFFIOStatRegister(void (*cb)(const char *url, int type, int bytes))
{
    ijkmp_io_stat_register(cb);
}

void IJKFFIOStatCompleteDebugCallback(const char *url,
                                      int64_t read_bytes, int64_t total_size,
                                      int64_t elpased_time, int64_t total_duration)
{
    if (!url)
        return;
    
    if (!av_strstart(url, "http:", NULL))
        return;
    
    NSLog(@"io-stat-complete: %s, %"PRId64"/%"PRId64", %"PRId64"/%"PRId64"\n",
          url, read_bytes, total_size, elpased_time, total_duration);
}

void IJKFFIOStatCompleteRegister(void (*cb)(const char *url,
                                            int64_t read_bytes, int64_t total_size,
                                            int64_t elpased_time, int64_t total_duration))
{
    ijkmp_io_stat_complete_register(cb);
}

- (id)initWithOptions:(IJKFFOptions *)options
{
    return [self initWithContentURL:nil withOptions:options];
}

- (id)initWithContentURL:(NSURL *)aUrl
             withOptions:(IJKFFOptions *)options
{
    if (aUrl == nil) {
        aUrl = [NSURL URLWithString:@("")];
    }
    
    // Detect if URL is file path and return proper string for it
    NSString *aUrlString = [aUrl isFileURL] ? [aUrl path] : [aUrl absoluteString];
    
    return [self initWithContentURLString:aUrlString
                              withOptions:options];
}

- (void)setScreenOn: (BOOL)on
{
    dispatch_async(dispatch_get_main_queue(), ^(){
        [IJKMediaModule sharedModule].mediaModuleIdleTimerDisabled = on;
    });
}

- (long)startWebRTC:(NSString *)dmToken
           withRealm:(NSString *)realm
       withNebulaAPI:(const NebulaAPI *)nebulaAPIs
{
    return [self startWebRTC:dmToken andRealm:realm andNebulaAPI:nebulaAPIs andStreamType:nil andStartTime:-1 andFileName:nil andChannelId:-1 andIsQuickConnect:true];
}

- (long)startWebRTC:(NSString *)dmToken
          andRealm:(NSString *)realm
       andNebulaAPI:(const NebulaAPI *)nebulaAPI
      andStreamType:(NSString *)streamType
       andStartTime:(int)playbackStartTime
        andFileName:(NSString *)playbackFileName
       andChannelId:(int)channelId
  andIsQuickConnect:(bool)isQuickConnect
{
    ARDSettingsModel *settingsModel = [[ARDSettingsModel alloc] init];
    settingsModel.dmToken = dmToken;
    settingsModel.realm = realm;
    settingsModel.channelId = channelId;
    settingsModel.streamType = streamType;
    settingsModel.playbackStartTime = playbackStartTime;
    settingsModel.playbackFileName = playbackFileName;
    settingsModel.isQuickConnect = isQuickConnect;
    
    _client = [[ARDAppClient alloc] initWithDelegate:self andNebulaAPI:nebulaAPI];
    long webrtc_id = [_client connectToRoomWithId:@"dummy" settings:settingsModel isLoopback:false];
    self.webrtcAPIs = [_client getWebRTCApi];
    return webrtc_id;
}

- (id)initWithContentURLString:(NSString *)aUrlString
                   withOptions:(IJKFFOptions *)options
{
    if (aUrlString == nil)
        return nil;
    
    self = [super init];
    if (self) {
        ijkmp_global_init();
        
        [IJKFFMoviePlayerController checkIfFFmpegVersionMatch:NO];
        
        if (options == nil)
            options = [IJKFFOptions optionsByDefault];
        
        [self addExtraOptions:options withUrl:aUrlString];
        
        // IJKFFIOStatRegister(IJKFFIOStatDebugCallback);
        // IJKFFIOStatCompleteRegister(IJKFFIOStatCompleteDebugCallback);
        
        // init fields
        _scalingMode = IJKMPMovieScalingModeAspectFit;
        _shouldAutoplay = YES;
        _monitor = [[IJKFFMonitor alloc] init];
        _inShutdown = NO;
        
        // init media resource
        _urlString = aUrlString;
        
        // init player
        _mediaPlayer = ijkmp_ios_create(media_player_msg_loop);
        _msgPool = [[IJKFFMoviePlayerMessagePool alloc] init];
        IJKWeakHolder *weakHolder = [IJKWeakHolder new];
        weakHolder.object = self;
        
        ijkmp_set_weak_thiz(_mediaPlayer, (__bridge_retained void *) self);
        ijkmp_set_inject_opaque(_mediaPlayer, (__bridge_retained void *) weakHolder);
        ijkmp_set_option_int(_mediaPlayer, IJKMP_OPT_CATEGORY_PLAYER, "start-on-prepared", _shouldAutoplay ? 1 : 0);
        
        // init video sink
        _glView = [[IJKSDLGLView alloc] initWithFrame:[[UIScreen mainScreen] bounds]];
        _glView.isThirdGLView = NO;
        _view = _glView;
        _hudViewController = [[IJKSDLHudViewController alloc] init];
        [_hudViewController setRect:_glView.frame];
        _shouldShowHudView = NO;
        _hudViewController.tableView.hidden = YES;
        [_view addSubview:_hudViewController.tableView];
        
        [self setHudValue:nil forKey:@"scheme"];
        [self setHudValue:nil forKey:@"host"];
        [self setHudValue:nil forKey:@"path"];
        [self setHudValue:nil forKey:@"ip"];
        [self setHudValue:nil forKey:@"tcp-info"];
        [self setHudValue:nil forKey:@"http"];
        [self setHudValue:nil forKey:@"tcp-spd"];
        [self setHudValue:nil forKey:@"t-prepared"];
        [self setHudValue:nil forKey:@"t-render"];
        [self setHudValue:nil forKey:@"t-preroll"];
        [self setHudValue:nil forKey:@"t-http-open"];
        [self setHudValue:nil forKey:@"t-http-seek"];
        
        self.shouldShowHudView = options.showHudView;
        
        ijkmp_ios_set_glview(_mediaPlayer, _glView);
        ijkmp_set_option(_mediaPlayer, IJKMP_OPT_CATEGORY_PLAYER, "overlay-format", "fcc-_es2");
#ifdef DEBUG
        [IJKFFMoviePlayerController setLogLevel:k_IJK_LOG_DEBUG];
#else
        [IJKFFMoviePlayerController setLogLevel:k_IJK_LOG_SILENT];
#endif

        int debug = (int)[options getOptionIntValue:@"debug" ofCategory:kIJKFFOptionCategoryPlayer];
        if (debug > 0) {
            [IJKFFMoviePlayerController setLogLevel:k_IJK_LOG_DEBUG];
        }
        
        // init audio sink
        [[IJKAudioKit sharedInstance] setupAudioSession:YES];
        
        [options applyTo:_mediaPlayer];
        _pauseInBackground = NO;
        
        // init extra
        _keepScreenOnWhilePlaying = YES;
        [self setScreenOn:YES];
        
        _notificationManager = [[IJKNotificationManager alloc] init];
        [self registerApplicationObservers];
        [self initCommon];
    }
    return self;
}

-(void) initCommon
{
    [self addPinchGesture];
    [self addPanGesture];
    self.minScale = 1.0f;
    self.maxScale = 3.0f;
    self.baseScale = 1.0f;
    self.lastPoint = CGPointMake(0, 0);
    self.frameSize = _glView.frame.size;
    self.onFling = NULL;
}

- (id)initWithMoreContent:(NSURL *)aUrl
              withOptions:(IJKFFOptions *)options
               withGLView:(UIView<IJKSDLGLViewProtocol> *)glView
{
    if (aUrl == nil)
        return nil;
    
    // Detect if URL is file path and return proper string for it
    NSString *aUrlString = [aUrl isFileURL] ? [aUrl path] : [aUrl absoluteString];
    
    return [self initWithMoreContentString:aUrlString
                               withOptions:options
                                withGLView:glView];
}

- (id)initWithMoreContentString:(NSString *)aUrlString
                    withOptions:(IJKFFOptions *)options
                     withGLView:(UIView <IJKSDLGLViewProtocol> *)glView
{
    if (aUrlString == nil || glView == nil)
        return nil;
    
    self = [super init];
    if (self) {
        ijkmp_global_init();
        
        [IJKFFMoviePlayerController checkIfFFmpegVersionMatch:NO];
        
        if (options == nil)
            options = [IJKFFOptions optionsByDefault];
        
        [self addExtraOptions:options withUrl:aUrlString];
        
        // IJKFFIOStatRegister(IJKFFIOStatDebugCallback);
        // IJKFFIOStatCompleteRegister(IJKFFIOStatCompleteDebugCallback);
        
        // init fields
        _scalingMode = IJKMPMovieScalingModeAspectFit;
        _shouldAutoplay = YES;
        _monitor = [[IJKFFMonitor alloc] init];
        _inShutdown = NO;
        
        // init media resource
        _urlString = aUrlString;
        
        // init player
        _mediaPlayer = ijkmp_ios_create(media_player_msg_loop);
        _msgPool = [[IJKFFMoviePlayerMessagePool alloc] init];
        IJKWeakHolder *weakHolder = [IJKWeakHolder new];
        weakHolder.object = self;
        
        ijkmp_set_weak_thiz(_mediaPlayer, (__bridge_retained void *) self);
        ijkmp_set_inject_opaque(_mediaPlayer, (__bridge_retained void *) weakHolder);
        ijkmp_set_option_int(_mediaPlayer, IJKMP_OPT_CATEGORY_PLAYER, "start-on-prepared", _shouldAutoplay ? 1 : 0);
        
        self.shouldShowHudView = options.showHudView;
        glView.isThirdGLView = YES;
        _view = _glView = (IJKSDLGLView *)glView;
        _hudViewController = [[IJKSDLHudViewController alloc] init];
        [_hudViewController setRect:_glView.frame];
        _shouldShowHudView = NO;
        _hudViewController.tableView.hidden = YES;
        [_view addSubview:_hudViewController.tableView];
        
        [self setHudValue:nil forKey:@"scheme"];
        [self setHudValue:nil forKey:@"host"];
        [self setHudValue:nil forKey:@"path"];
        [self setHudValue:nil forKey:@"ip"];
        [self setHudValue:nil forKey:@"tcp-info"];
        [self setHudValue:nil forKey:@"http"];
        [self setHudValue:nil forKey:@"tcp-spd"];
        [self setHudValue:nil forKey:@"t-prepared"];
        [self setHudValue:nil forKey:@"t-render"];
        [self setHudValue:nil forKey:@"t-preroll"];
        [self setHudValue:nil forKey:@"t-http-open"];
        [self setHudValue:nil forKey:@"t-http-seek"];
        self.shouldShowHudView = options.showHudView;
        
        ijkmp_ios_set_glview(_mediaPlayer, _glView);
        
        ijkmp_set_option(_mediaPlayer, IJKMP_OPT_CATEGORY_PLAYER, "overlay-format", "fcc-_es2");
#ifdef DEBUG
        [IJKFFMoviePlayerController setLogLevel:k_IJK_LOG_DEBUG];
#else
        [IJKFFMoviePlayerController setLogLevel:k_IJK_LOG_SILENT];
#endif
        // init audio sink
        [[IJKAudioKit sharedInstance] setupAudioSession:YES];
        
        [options applyTo:_mediaPlayer];
        _pauseInBackground = NO;
        
        // init extra
        _keepScreenOnWhilePlaying = YES;
        [self setScreenOn:YES];
        
        _notificationManager = [[IJKNotificationManager alloc] init];
        [self registerApplicationObservers];
        [self initCommon];
    }
    return self;
}

- (void)setVideoPath:(NSString *)path
{
    _urlString = path;

    if (self.webrtcAPIs) {
        IJKFFOptions *options = [[IJKFFOptions alloc] init];
        [options setFormatOptionIntValue:(int64_t)self.webrtcAPIs forKey:@"webrtc_api"];
        [self addExtraOptions:options withUrl:_urlString];
        [options applyTo:_mediaPlayer];
    }
}

- (void)addExtraOptions:(IJKFFOptions *)options
                withUrl:(NSString *)aUrlString
{
  if ([aUrlString hasPrefix:@"rtsp://"] || [aUrlString hasPrefix:@"avapi://"] ||
      [aUrlString hasPrefix:@"webrtc://"]) {
        if (![options hasOptionValue:@"analyzemaxduration" ofCategory:kIJKFFOptionCategoryFormat]) {
            [options setOptionIntValue:100 forKey:@"analyzemaxduration" ofCategory:kIJKFFOptionCategoryFormat];
        }
        if (![options hasOptionValue:@"probesize" ofCategory:kIJKFFOptionCategoryFormat]) {
            [options setOptionIntValue:10240 forKey:@"probesize" ofCategory:kIJKFFOptionCategoryFormat];
        }
        if (![options hasOptionValue:@"flush_packets" ofCategory:kIJKFFOptionCategoryFormat]) {
            [options setOptionIntValue:1 forKey:@"flush_packets" ofCategory:kIJKFFOptionCategoryFormat];
        }
        if (![options hasOptionValue:@"packet-buffering" ofCategory:kIJKFFOptionCategoryPlayer]) {
            [options setOptionIntValue:0 forKey:@"packet-buffering" ofCategory:kIJKFFOptionCategoryPlayer];
        }
    }
    
    if ([aUrlString hasPrefix:@"rtsp://"]) {
        if (![options hasOptionValue:@"rtsp_flags" ofCategory:kIJKFFOptionCategoryFormat]) {
            [options setOptionValue:@"prefer_tcp" forKey:@"rtsp_flags" ofCategory:kIJKFFOptionCategoryFormat];
        }
    }
}

- (void)dealloc
{
    //    [self unregisterApplicationObservers];
}

- (void)setShouldAutoplay:(BOOL)shouldAutoplay
{
    _shouldAutoplay = shouldAutoplay;
    
    if (!_mediaPlayer)
        return;
    
    ijkmp_set_option_int(_mediaPlayer, IJKMP_OPT_CATEGORY_PLAYER, "start-on-prepared", _shouldAutoplay ? 1 : 0);
}

- (BOOL)shouldAutoplay
{
    return _shouldAutoplay;
}

- (void)prepareToPlay
{
    NSLog(@"ijkvideoview version: %s\n", kIJKVideoViewVersion);
    
    if (!_mediaPlayer)
        return;
    
    self.inShutdown = NO;
    self.currentX = -1;
    self.currentY = -1;
    self.lastFoundObjectTime = -1.0;
    [self setScreenOn:_keepScreenOnWhilePlaying];
    
    ijkmp_set_data_source(_mediaPlayer, [_urlString UTF8String]);
    ijkmp_set_option(_mediaPlayer, IJKMP_OPT_CATEGORY_FORMAT, "safe", "0"); // for concat demuxer
    
    _monitor.prepareStartTick = (int64_t)SDL_GetTickHR();
    ijkmp_prepare_async(_mediaPlayer);
}

- (void)setHudUrl:(NSString *)urlString
{
    if ([[NSThread currentThread] isMainThread]) {
        NSURL *url = [NSURL URLWithString:urlString];
        [self setHudValue:url.scheme forKey:@"scheme"];
        [self setHudValue:url.host   forKey:@"host"];
        [self setHudValue:url.path   forKey:@"path"];
    } else {
        dispatch_async(dispatch_get_main_queue(), ^{
            [self setHudUrl:urlString];
        });
    }
}

- (void)play
{
    if (!_mediaPlayer)
        return;
    
    [self setScreenOn:_keepScreenOnWhilePlaying];
    
    [self startHudTimer];
    ijkmp_start(_mediaPlayer);
}

- (void)pause
{
    if (!_mediaPlayer)
        return;
    
    //    [self stopHudTimer];
    ijkmp_pause(_mediaPlayer);
}

- (void)stop
{
    if (!_mediaPlayer)
        return;
    
    [self setScreenOn:NO];
    
    [self stopHudTimer];
    ijkmp_stop(_mediaPlayer);
    [_glView stop];
}

- (BOOL)isPlaying
{
    if (!_mediaPlayer)
        return NO;
    
    return ijkmp_is_playing(_mediaPlayer);
}

- (void)setPauseInBackground:(BOOL)pause
{
    _pauseInBackground = pause;
}

- (BOOL)isVideoToolboxOpen
{
    if (!_mediaPlayer)
        return NO;
    
    return _isVideoToolboxOpen;
}

inline static int getPlayerOption(IJKFFOptionCategory category)
{
    int mp_category = -1;
    switch (category) {
        case kIJKFFOptionCategoryFormat:
            mp_category = IJKMP_OPT_CATEGORY_FORMAT;
            break;
        case kIJKFFOptionCategoryCodec:
            mp_category = IJKMP_OPT_CATEGORY_CODEC;
            break;
        case kIJKFFOptionCategorySws:
            mp_category = IJKMP_OPT_CATEGORY_SWS;
            break;
        case kIJKFFOptionCategoryPlayer:
            mp_category = IJKMP_OPT_CATEGORY_PLAYER;
            break;
        default:
            NSLog(@"unknown option category: %d\n", category);
    }
    return mp_category;
}

- (void)setOptionValue:(NSString *)value
                forKey:(NSString *)key
            ofCategory:(IJKFFOptionCategory)category
{
    assert(_mediaPlayer);
    if (!_mediaPlayer)
        return;
    
    ijkmp_set_option(_mediaPlayer, getPlayerOption(category), [key UTF8String], [value UTF8String]);
}

- (void)setOptionIntValue:(int64_t)value
                   forKey:(NSString *)key
               ofCategory:(IJKFFOptionCategory)category
{
    assert(_mediaPlayer);
    if (!_mediaPlayer)
        return;
    
    ijkmp_set_option_int(_mediaPlayer, getPlayerOption(category), [key UTF8String], value);
}

+ (void)setLogReport:(BOOL)preferLogReport
{
    ijkmp_global_set_log_report(preferLogReport ? 1 : 0);
}

+ (void)setLogLevel:(IJKLogLevel)logLevel
{
    ijkmp_global_set_log_level(logLevel);
}

+ (BOOL)checkIfFFmpegVersionMatch:(BOOL)showAlert;
{
    const char *actualVersion = av_version_info();
    const char *expectVersion = kIJKFFRequiredFFmpegVersion;
    if (0 == strcmp(actualVersion, expectVersion)) {
        return YES;
    } else {
        NSString *message = [NSString stringWithFormat:@"actual: %s\n expect: %s\n", actualVersion, expectVersion];
        NSLog(@"\n!!!!!!!!!!\n%@\n!!!!!!!!!!\n", message);
        if (showAlert) {
            UIAlertView *alertView = [[UIAlertView alloc] initWithTitle:@"Unexpected FFmpeg version"
                                                                message:message
                                                               delegate:nil
                                                      cancelButtonTitle:@"OK"
                                                      otherButtonTitles:nil];
            [alertView show];
        }
        return NO;
    }
}

+ (BOOL)checkIfPlayerVersionMatch:(BOOL)showAlert
                          version:(NSString *)version
{
    const char *actualVersion = ijkmp_version();
    const char *expectVersion = version.UTF8String;
    if (0 == strcmp(actualVersion, expectVersion)) {
        return YES;
    } else {
        if (showAlert) {
            NSString *message = [NSString stringWithFormat:@"actual: %s\n expect: %s\n",
                                 actualVersion, expectVersion];
            UIAlertView *alertView = [[UIAlertView alloc] initWithTitle:@"Unexpected ijkplayer version"
                                                                message:message
                                                               delegate:nil
                                                      cancelButtonTitle:@"OK"
                                                      otherButtonTitles:nil];
            [alertView show];
        }
        return NO;
    }
}

- (void)shutdown
{
    [self shutdown:YES];
}

- (void)shutdown:(BOOL)async
{
    if (!_mediaPlayer)
        return;
    
    self.inShutdown = YES;
    ijkmp_stop(_mediaPlayer);
    [_glView stop];
    [self stopHudTimer];
    [self unregisterApplicationObservers];
    [self setScreenOn:NO];
    self.asyncShutdown = async;

    if (!async) {
        ijkmp_shutdown(_mediaPlayer);
    }
    [self performSelectorInBackground:@selector(shutdownWaitStop:) withObject:self];
}

- (void)shutdownWaitStop:(IJKFFMoviePlayerController *) mySelf
{
    if (!_mediaPlayer)
        return;

    if (self.asyncShutdown) {
        ijkmp_shutdown(_mediaPlayer);
    }
    [self stopWebRTC];
    [self performSelectorOnMainThread:@selector(shutdownClose:) withObject:self waitUntilDone:YES];
}

- (void)shutdownClose:(IJKFFMoviePlayerController *) mySelf
{
    if (!_mediaPlayer)
        return;
    
    _segmentOpenDelegate    = nil;
    _tcpOpenDelegate        = nil;
    _httpOpenDelegate       = nil;
    _liveOpenDelegate       = nil;
    _nativeInvokeDelegate   = nil;
    
    __unused id weakPlayer = (__bridge_transfer IJKFFMoviePlayerController*)ijkmp_set_weak_thiz(_mediaPlayer, NULL);
    __unused id weakHolder = (__bridge_transfer IJKWeakHolder*)ijkmp_set_inject_opaque(_mediaPlayer, NULL);
    ijkmp_dec_ref_p(&_mediaPlayer);
    
    [self didShutdown];
}

- (void)didShutdown
{
}

- (IJKMPMoviePlaybackState)playbackState
{
    if (!_mediaPlayer)
        return NO;
    
    IJKMPMoviePlaybackState mpState = IJKMPMoviePlaybackStateStopped;
    int state = ijkmp_get_state(_mediaPlayer);
    switch (state) {
        case MP_STATE_STOPPED:
        case MP_STATE_COMPLETED:
        case MP_STATE_ERROR:
        case MP_STATE_END:
            mpState = IJKMPMoviePlaybackStateStopped;
            break;
        case MP_STATE_IDLE:
        case MP_STATE_INITIALIZED:
        case MP_STATE_ASYNC_PREPARING:
        case MP_STATE_PAUSED:
            mpState = IJKMPMoviePlaybackStatePaused;
            break;
        case MP_STATE_PREPARED:
        case MP_STATE_STARTED: {
            if (_seeking)
                mpState = IJKMPMoviePlaybackStateSeekingForward;
            else
                mpState = IJKMPMoviePlaybackStatePlaying;
            break;
        }
    }
    // IJKMPMoviePlaybackStatePlaying,
    // IJKMPMoviePlaybackStatePaused,
    // IJKMPMoviePlaybackStateStopped,
    // IJKMPMoviePlaybackStateInterrupted,
    // IJKMPMoviePlaybackStateSeekingForward,
    // IJKMPMoviePlaybackStateSeekingBackward
    return mpState;
}

- (void)setCurrentPlaybackTime:(NSTimeInterval)aCurrentPlaybackTime
{
    if (!_mediaPlayer)
        return;
    
    _seeking = YES;
    [[NSNotificationCenter defaultCenter]
     postNotificationName:IJKMPMoviePlayerPlaybackStateDidChangeNotification
     object:self];
    
    _bufferingPosition = 0;
    ijkmp_seek_to(_mediaPlayer, aCurrentPlaybackTime * 1000);
}

- (NSTimeInterval)currentPlaybackTime
{
    if (!_mediaPlayer)
        return 0.0f;
    
    NSTimeInterval ret = ijkmp_get_current_position(_mediaPlayer);
    if (isnan(ret) || isinf(ret))
        return -1;
    
    return ret / 1000;
}

- (NSTimeInterval)currentRecordingTime
{
    if (!_mediaPlayer)
        return 0.0f;
    
    NSTimeInterval ret = ijkmp_get_recording_position(_mediaPlayer);
    if (isnan(ret) || isinf(ret))
        return -1;
    
    return ret / 1000;
}

- (NSTimeInterval)realTime
{
    if (!_mediaPlayer)
        return 0.0f;
    
    NSTimeInterval ret = ijkmp_get_real_time(_mediaPlayer);
    if (isnan(ret) || isinf(ret))
        return -1;
    
    return ret;
}

- (NSInteger)avtechPlaybackStatus
{
    if (!_mediaPlayer)
        return 0;
    
    return ijkmp_get_avtech_playback_status(_mediaPlayer);
}

- (NSTimeInterval)duration
{
    if (!_mediaPlayer)
        return 0.0f;
    
    NSTimeInterval ret = ijkmp_get_duration(_mediaPlayer);
    if (isnan(ret) || isinf(ret))
        return -1;
    
    return ret / 1000;
}

- (NSTimeInterval)playableDuration
{
    if (!_mediaPlayer)
        return 0.0f;
    
    NSTimeInterval demux_cache = ((NSTimeInterval)ijkmp_get_playable_duration(_mediaPlayer)) / 1000;
    return demux_cache;
}

- (CGSize)naturalSize
{
    return _naturalSize;
}

- (void)changeNaturalSize
{
    [self willChangeValueForKey:@"naturalSize"];
    if (_sampleAspectRatioNumerator > 0 && _sampleAspectRatioDenominator > 0) {
        self->_naturalSize = CGSizeMake(1.0f * _videoWidth * _sampleAspectRatioNumerator / _sampleAspectRatioDenominator, _videoHeight);
    } else {
        self->_naturalSize = CGSizeMake(_videoWidth, _videoHeight);
    }
    [self didChangeValueForKey:@"naturalSize"];
    
    if (self->_naturalSize.width > 0 && self->_naturalSize.height > 0) {
        [[NSNotificationCenter defaultCenter]
         postNotificationName:IJKMPMovieNaturalSizeAvailableNotification
         object:self];
    }
}

- (void)setScalingMode: (IJKMPMovieScalingMode) aScalingMode
{
    IJKMPMovieScalingMode newScalingMode = aScalingMode;
    switch (aScalingMode) {
        case IJKMPMovieScalingModeNone:
            [_view setContentMode:UIViewContentModeCenter];
            break;
        case IJKMPMovieScalingModeAspectFit:
            [_view setContentMode:UIViewContentModeScaleAspectFit];
            break;
        case IJKMPMovieScalingModeAspectFill:
            [_view setContentMode:UIViewContentModeScaleAspectFill];
            break;
        case IJKMPMovieScalingModeFill:
            [_view setContentMode:UIViewContentModeScaleToFill];
            break;
        default:
            newScalingMode = _scalingMode;
    }
    
    _scalingMode = newScalingMode;
}

// deprecated, for MPMoviePlayerController compatiable
- (UIImage *)thumbnailImageAtTime:(NSTimeInterval)playbackTime timeOption:(IJKMPMovieTimeOption)option
{
    return nil;
}

- (UIImage *)thumbnailImageAtCurrentTime
{
    if ([_view conformsToProtocol:@protocol(IJKSDLGLViewProtocol)]) {
        id<IJKSDLGLViewProtocol> glView = (id<IJKSDLGLViewProtocol>)_view;
        return [glView snapshot];
    }
    
    return nil;
}

- (CGFloat)fpsAtOutput
{
    return _glView.fps;
}

inline static NSString *formatedDurationMilli(int64_t duration) {
    if (duration >=  1000) {
        return [NSString stringWithFormat:@"%.2f sec", ((float)duration) / 1000];
    } else {
        return [NSString stringWithFormat:@"%ld msec", (long)duration];
    }
}

inline static NSString *formatedDurationBytesAndBitrate(int64_t bytes, int64_t bitRate) {
    if (bitRate <= 0) {
        return @"inf";
    }
    return formatedDurationMilli(((float)bytes) * 8 * 1000 / bitRate);
}

inline static NSString *formatedSize(int64_t bytes) {
    if (bytes >= 100 * 1024) {
        return [NSString stringWithFormat:@"%.2f MB", ((float)bytes) / 1000 / 1024];
    } else if (bytes >= 100) {
        return [NSString stringWithFormat:@"%.1f KB", ((float)bytes) / 1000];
    } else {
        return [NSString stringWithFormat:@"%ld B", (long)bytes];
    }
}

inline static NSString *formatedSpeed(int64_t bytes, int64_t elapsed_milli) {
    if (elapsed_milli <= 0) {
        return @"N/A";
    }
    
    if (bytes <= 0) {
        return @"0";
    }
    
    float bytes_per_sec = ((float)bytes) * 1000.f /  elapsed_milli;
    if (bytes_per_sec >= 1000 * 1000) {
        return [NSString stringWithFormat:@"%.2f MB/s", ((float)bytes_per_sec) / 1000 / 1000];
    } else if (bytes_per_sec >= 1000) {
        return [NSString stringWithFormat:@"%.1f KB/s", ((float)bytes_per_sec) / 1000];
    } else {
        return [NSString stringWithFormat:@"%ld B/s", (long)bytes_per_sec];
    }
}

- (void)refreshHudView
{
    if (_mediaPlayer == nil)
        return;
    
    int64_t vdec = ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_VIDEO_DECODER, FFP_PROPV_DECODER_UNKNOWN);
    float   vdps = ijkmp_get_property_float(_mediaPlayer, FFP_PROP_FLOAT_VIDEO_DECODE_FRAMES_PER_SECOND, .0f);
    float   vfps = ijkmp_get_property_float(_mediaPlayer, FFP_PROP_FLOAT_VIDEO_OUTPUT_FRAMES_PER_SECOND, .0f);
    
    switch (vdec) {
        case FFP_PROPV_DECODER_VIDEOTOOLBOX:
            [self setHudValue:@"VideoToolbox" forKey:@"vdec"];
            break;
        case FFP_PROPV_DECODER_AVCODEC:
            [self setHudValue:[NSString stringWithFormat:@"avcodec %d.%d.%d",
                               LIBAVCODEC_VERSION_MAJOR,
                               LIBAVCODEC_VERSION_MINOR,
                               LIBAVCODEC_VERSION_MICRO]
                       forKey:@"vdec"];
            break;
        default:
            [self setHudValue:@"N/A" forKey:@"vdec"];
            break;
    }
    
    [self setHudValue:[NSString stringWithFormat:@"%.2f / %.2f", vdps, vfps] forKey:@"fps"];
    
    int64_t vcacheb = ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_VIDEO_CACHED_BYTES, 0);
    int64_t acacheb = ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_AUDIO_CACHED_BYTES, 0);
    int64_t vcached = ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_VIDEO_CACHED_DURATION, 0);
    int64_t acached = ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_AUDIO_CACHED_DURATION, 0);
    int64_t vcachep = ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_VIDEO_CACHED_PACKETS, 0);
    int64_t acachep = ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_AUDIO_CACHED_PACKETS, 0);
    [self setHudValue:[NSString stringWithFormat:@"%@, %@, %"PRId64" packets",
                       formatedDurationMilli(vcached),
                       formatedSize(vcacheb),
                       vcachep]
               forKey:@"v-cache"];
    [self setHudValue:[NSString stringWithFormat:@"%@, %@, %"PRId64" packets",
                       formatedDurationMilli(acached),
                       formatedSize(acacheb),
                       acachep]
               forKey:@"a-cache"];
    
    float avdelay = ijkmp_get_property_float(_mediaPlayer, FFP_PROP_FLOAT_AVDELAY, .0f);
    float avdiff  = ijkmp_get_property_float(_mediaPlayer, FFP_PROP_FLOAT_AVDIFF, .0f);
    [self setHudValue:[NSString stringWithFormat:@"%.3f %.3f", avdelay, -avdiff] forKey:@"delay"];
        
    int64_t tcpSpeed = ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_TCP_SPEED, 0);
    [self setHudValue:[NSString stringWithFormat:@"%@", formatedSpeed(tcpSpeed, 1000)]
               forKey:@"tcp-spd"];
    
    [self setHudValue:formatedDurationMilli(_monitor.prepareDuration) forKey:@"t-prepared"];
    [self setHudValue:formatedDurationMilli(_monitor.firstVideoFrameLatency) forKey:@"t-render"];
    [self setHudValue:formatedDurationMilli(_monitor.lastPrerollDuration) forKey:@"t-preroll"];
    [self setHudValue:[NSString stringWithFormat:@"%@ / %d",
                       formatedDurationMilli(_monitor.lastHttpOpenDuration),
                       _monitor.httpOpenCount]
               forKey:@"t-http-open"];
    [self setHudValue:[NSString stringWithFormat:@"%@ / %d",
                       formatedDurationMilli(_monitor.lastHttpSeekDuration),
                       _monitor.httpSeekCount]
               forKey:@"t-http-seek"];
}

- (void)startHudTimer
{
    if (!_shouldShowHudView)
        return;
    
    if (_hudTimer != nil)
        return;
    
    if ([[NSThread currentThread] isMainThread]) {
        _hudViewController.tableView.hidden = NO;
        _hudTimer = [NSTimer scheduledTimerWithTimeInterval:.5f
                                                     target:self
                                                   selector:@selector(refreshHudView)
                                                   userInfo:nil
                                                    repeats:YES];
    } else {
        dispatch_async(dispatch_get_main_queue(), ^{
            [self startHudTimer];
        });
    }
}

- (void)stopHudTimer
{
    if (_hudTimer == nil)
        return;
    
    if ([[NSThread currentThread] isMainThread]) {
        _hudViewController.tableView.hidden = YES;
        [_hudTimer invalidate];
        _hudTimer = nil;
    } else {
        dispatch_async(dispatch_get_main_queue(), ^{
            [self stopHudTimer];
        });
    }
}

- (void)setShouldShowHudView:(BOOL)shouldShowHudView
{
    if (shouldShowHudView == _shouldShowHudView) {
        return;
    }
    _shouldShowHudView = shouldShowHudView;
    if (shouldShowHudView)
        [self startHudTimer];
    else
        [self stopHudTimer];
}

- (BOOL)shouldShowHudView
{
    return _shouldShowHudView;
}

- (void)setPlaybackRate:(float)playbackRate
{
    if (!_mediaPlayer)
        return;
    
    return ijkmp_set_playback_rate(_mediaPlayer, playbackRate);
}

- (float)playbackRate
{
    if (!_mediaPlayer)
        return 0.0f;
    
    return ijkmp_get_property_float(_mediaPlayer, FFP_PROP_FLOAT_PLAYBACK_RATE, 0.0f);
}

- (void)setPlaybackVolume:(float)volume
{
    if (!_mediaPlayer)
        return;
    return ijkmp_set_playback_volume(_mediaPlayer, volume);
}

- (float)playbackVolume
{
    if (!_mediaPlayer)
        return 0.0f;
    return ijkmp_get_property_float(_mediaPlayer, FFP_PROP_FLOAT_PLAYBACK_VOLUME, 1.0f);
}

- (int64_t)getFileSize
{
    if (!_mediaPlayer)
        return 0;
    return ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_LOGICAL_FILE_SIZE, 0);
}

- (int64_t)trafficStatistic
{
    if (!_mediaPlayer)
        return 0;
    return ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_TRAFFIC_STATISTIC_BYTE_COUNT, 0);
}

- (float)dropFrameRate
{
    if (!_mediaPlayer)
        return 0;
    return ijkmp_get_property_float(_mediaPlayer, FFP_PROP_FLOAT_DROP_FRAME_RATE, 0.0f);
}

- (int64_t)videoFrameTimestamp
{
    if (!_mediaPlayer)
        return 0;
    return ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_VIDEO_FRAME_TIMESTAMP, 0);
}

- (float)videoDecodeFramesPerSecond
{
    if (!_mediaPlayer)
        return 0.0f;
    return ijkmp_get_property_float(_mediaPlayer, FFP_PROP_FLOAT_VIDEO_DECODE_FRAMES_PER_SECOND, 0.0f);
}

- (float)videoOutputFramesPerSecond
{
    if (!_mediaPlayer)
        return 0.0f;
    return ijkmp_get_property_float(_mediaPlayer, FFP_PROP_FLOAT_VIDEO_OUTPUT_FRAMES_PER_SECOND, 0.0f);
}

- (int64_t)videoBitRate
{
    if (!_mediaPlayer)
        return 0;
    return ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_BIT_RATE, 0);
}

- (int64_t)networkBitRate
{
    if (!_mediaPlayer)
        return 0;
    return ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_NETWORK_BIT_RATE, 0);
}

- (int64_t)videoCachedDuration
{
    if (!_mediaPlayer)
        return 0;
    return ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_VIDEO_CACHED_DURATION, 0);
}

- (int64_t)audioCachedDuration
{
    if (!_mediaPlayer)
        return 0;
    return ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_AUDIO_CACHED_DURATION, 0);
}

- (float)avdiff
{
    if (!_mediaPlayer)
        return 0.0f;
    return ijkmp_get_property_float(_mediaPlayer, FFP_PROP_FLOAT_AVDIFF, 0.0f);
}


inline static void fillMetaInternal(NSMutableDictionary *meta, IjkMediaMeta *rawMeta, const char *name, NSString *defaultValue)
{
    if (!meta || !rawMeta || !name)
        return;
    
    NSString *key = [NSString stringWithUTF8String:name];
    const char *value = ijkmeta_get_string_l(rawMeta, name);
    if (value) {
        [meta setObject:[NSString stringWithUTF8String:value] forKey:key];
    } else if (defaultValue) {
        [meta setObject:defaultValue forKey:key];
    } else {
        [meta removeObjectForKey:key];
    }
}

- (void)postEvent: (IJKFFMoviePlayerMessage *)msg
{
    if (!msg)
        return;
    
    AVMessage *avmsg = &msg->_msg;
    switch (avmsg->what) {
        case FFP_MSG_FLUSH:
            break;
        case FFP_MSG_ERROR: {
            NSLog(@"FFP_MSG_ERROR: %d\n", avmsg->arg1);
            
            [self setScreenOn:NO];
            
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerPlaybackStateDidChangeNotification
             object:self];
            
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerPlaybackDidFinishNotification
             object:self
             userInfo:@{
                        IJKMPMoviePlayerPlaybackDidFinishReasonUserInfoKey: @(IJKMPMovieFinishReasonPlaybackError),
                        @"error": @(avmsg->arg1)}];
            if (self.onDownloadCompleted != NULL) {
                self.onDownloadCompleted(avmsg->arg1);
            }
            break;
        }
        case FFP_MSG_PREPARED: {
            NSLog(@"FFP_MSG_PREPARED:\n");
            
            _monitor.prepareDuration = (int64_t)SDL_GetTickHR() - _monitor.prepareStartTick;
            int64_t vdec = ijkmp_get_property_int64(_mediaPlayer, FFP_PROP_INT64_VIDEO_DECODER, FFP_PROPV_DECODER_UNKNOWN);
            switch (vdec) {
                case FFP_PROPV_DECODER_VIDEOTOOLBOX:
                    _monitor.vdecoder = @"VideoToolbox";
                    break;
                case FFP_PROPV_DECODER_AVCODEC:
                    _monitor.vdecoder = [NSString stringWithFormat:@"avcodec %d.%d.%d",
                                         LIBAVCODEC_VERSION_MAJOR,
                                         LIBAVCODEC_VERSION_MINOR,
                                         LIBAVCODEC_VERSION_MICRO];
                    break;
                default:
                    _monitor.vdecoder = @"Unknown";
                    break;
            }
            
            IjkMediaMeta *rawMeta = ijkmp_get_meta_l(_mediaPlayer);
            if (rawMeta) {
                ijkmeta_lock(rawMeta);
                
                NSMutableDictionary *newMediaMeta = [[NSMutableDictionary alloc] init];
                
                fillMetaInternal(newMediaMeta, rawMeta, IJKM_KEY_FORMAT, nil);
                fillMetaInternal(newMediaMeta, rawMeta, IJKM_KEY_DURATION_US, nil);
                fillMetaInternal(newMediaMeta, rawMeta, IJKM_KEY_START_US, nil);
                fillMetaInternal(newMediaMeta, rawMeta, IJKM_KEY_BITRATE, nil);
                
                fillMetaInternal(newMediaMeta, rawMeta, IJKM_KEY_VIDEO_STREAM, nil);
                fillMetaInternal(newMediaMeta, rawMeta, IJKM_KEY_AUDIO_STREAM, nil);
                
                int64_t video_stream = ijkmeta_get_int64_l(rawMeta, IJKM_KEY_VIDEO_STREAM, -1);
                int64_t audio_stream = ijkmeta_get_int64_l(rawMeta, IJKM_KEY_AUDIO_STREAM, -1);
                
                NSMutableArray *streams = [[NSMutableArray alloc] init];
                
                size_t count = ijkmeta_get_children_count_l(rawMeta);
                for(size_t i = 0; i < count; ++i) {
                    IjkMediaMeta *streamRawMeta = ijkmeta_get_child_l(rawMeta, i);
                    NSMutableDictionary *streamMeta = [[NSMutableDictionary alloc] init];
                    
                    if (streamRawMeta) {
                        fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_TYPE, k_IJKM_VAL_TYPE__UNKNOWN);
                        const char *type = ijkmeta_get_string_l(streamRawMeta, IJKM_KEY_TYPE);
                        if (type) {
                            fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_CODEC_NAME, nil);
                            fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_CODEC_PROFILE, nil);
                            fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_CODEC_LONG_NAME, nil);
                            fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_BITRATE, nil);
                            
                            if (0 == strcmp(type, IJKM_VAL_TYPE__VIDEO)) {
                                fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_WIDTH, nil);
                                fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_HEIGHT, nil);
                                fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_FPS_NUM, nil);
                                fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_FPS_DEN, nil);
                                fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_TBR_NUM, nil);
                                fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_TBR_DEN, nil);
                                fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_SAR_NUM, nil);
                                fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_SAR_DEN, nil);
                                
                                if (video_stream == i) {
                                    _monitor.videoMeta = streamMeta;
                                    
                                    int64_t fps_num = ijkmeta_get_int64_l(streamRawMeta, IJKM_KEY_FPS_NUM, 0);
                                    int64_t fps_den = ijkmeta_get_int64_l(streamRawMeta, IJKM_KEY_FPS_DEN, 0);
                                    if (fps_num > 0 && fps_den > 0) {
                                        _fpsInMeta = ((CGFloat)(fps_num)) / fps_den;
                                        NSLog(@"fps in meta %f\n", _fpsInMeta);
                                    }
                                }
                                
                            } else if (0 == strcmp(type, IJKM_VAL_TYPE__AUDIO)) {
                                fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_SAMPLE_RATE, nil);
                                fillMetaInternal(streamMeta, streamRawMeta, IJKM_KEY_CHANNEL_LAYOUT, nil);
                                
                                if (audio_stream == i) {
                                    _monitor.audioMeta = streamMeta;
                                }
                            }
                        }
                    }
                    
                    [streams addObject:streamMeta];
                }
                
                [newMediaMeta setObject:streams forKey:kk_IJKM_KEY_STREAMS];
                
                ijkmeta_unlock(rawMeta);
                _monitor.mediaMeta = newMediaMeta;
            }
            ijkmp_set_playback_rate(_mediaPlayer, [self playbackRate]);
            ijkmp_set_playback_volume(_mediaPlayer, [self playbackVolume]);
            
            [self startHudTimer];
            _isPreparedToPlay = YES;
            
            [[NSNotificationCenter defaultCenter] postNotificationName:IJKMPMediaPlaybackIsPreparedToPlayDidChangeNotification object:self];
            _loadState = IJKMPMovieLoadStatePlayable | IJKMPMovieLoadStatePlaythroughOK;
            
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerLoadStateDidChangeNotification
             object:self];
            
            break;
        }
        case FFP_MSG_COMPLETED: {
            
            [self setScreenOn:NO];
                        
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerPlaybackDidFinishNotification
             object:self
             userInfo:@{IJKMPMoviePlayerPlaybackDidFinishReasonUserInfoKey: @(IJKMPMovieFinishReasonPlaybackEnded)}];
            break;
        }
        case FFP_MSG_VIDEO_SIZE_CHANGED:
            NSLog(@"FFP_MSG_VIDEO_SIZE_CHANGED: %d, %d\n", avmsg->arg1, avmsg->arg2);
            if (avmsg->arg1 > 0)
                _videoWidth = avmsg->arg1;
            if (avmsg->arg2 > 0)
                _videoHeight = avmsg->arg2;
            [self changeNaturalSize];
            self.frameSize = _glView.frame.size;
            break;
        case FFP_MSG_SAR_CHANGED:
            NSLog(@"FFP_MSG_SAR_CHANGED: %d, %d\n", avmsg->arg1, avmsg->arg2);
            if (avmsg->arg1 > 0)
                _sampleAspectRatioNumerator = avmsg->arg1;
            if (avmsg->arg2 > 0)
                _sampleAspectRatioDenominator = avmsg->arg2;
            [self changeNaturalSize];
            break;
        case FFP_MSG_BUFFERING_START: {
            NSLog(@"FFP_MSG_BUFFERING_START:\n");
            
            _monitor.lastPrerollStartTick = (int64_t)SDL_GetTickHR();
            
            _loadState = IJKMPMovieLoadStateStalled;
            _isSeekBuffering = avmsg->arg1;
            
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerLoadStateDidChangeNotification
             object:self];
            _isSeekBuffering = 0;
            break;
        }
        case FFP_MSG_BUFFERING_END: {
            NSLog(@"FFP_MSG_BUFFERING_END:\n");
            
            _monitor.lastPrerollDuration = (int64_t)SDL_GetTickHR() - _monitor.lastPrerollStartTick;
            
            _loadState = IJKMPMovieLoadStatePlayable | IJKMPMovieLoadStatePlaythroughOK;
            _isSeekBuffering = avmsg->arg1;
            
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerLoadStateDidChangeNotification
             object:self];
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerPlaybackStateDidChangeNotification
             object:self];
            _isSeekBuffering = 0;
            break;
        }
        case FFP_MSG_BUFFERING_UPDATE:
            _bufferingPosition = avmsg->arg1;
            _bufferingProgress = avmsg->arg2;
            // NSLog(@"FFP_MSG_BUFFERING_UPDATE: %d, %%%d\n", _bufferingPosition, _bufferingProgress);
            break;
        case FFP_MSG_BUFFERING_BYTES_UPDATE:
            // NSLog(@"FFP_MSG_BUFFERING_BYTES_UPDATE: %d\n", avmsg->arg1);
            break;
        case FFP_MSG_BUFFERING_TIME_UPDATE:
            _bufferingTime       = avmsg->arg1;
            // NSLog(@"FFP_MSG_BUFFERING_TIME_UPDATE: %d\n", avmsg->arg1);
            break;
        case FFP_MSG_PLAYBACK_STATE_CHANGED:
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerPlaybackStateDidChangeNotification
             object:self];
            break;
        case FFP_MSG_SEEK_COMPLETE: {
            NSLog(@"FFP_MSG_SEEK_COMPLETE:\n");
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerDidSeekCompleteNotification
             object:self
             userInfo:@{IJKMPMoviePlayerDidSeekCompleteTargetKey: @(avmsg->arg1),
                        IJKMPMoviePlayerDidSeekCompleteErrorKey: @(avmsg->arg2)}];
            _seeking = NO;
            break;
        }
        case FFP_MSG_VIDEO_DECODER_OPEN: {
            _isVideoToolboxOpen = avmsg->arg1;
            NSLog(@"FFP_MSG_VIDEO_DECODER_OPEN: %@\n", _isVideoToolboxOpen ? @"true" : @"false");
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerVideoDecoderOpenNotification
             object:self];
            break;
        }
        case FFP_MSG_VIDEO_RENDERING_START: {
            NSLog(@"FFP_MSG_VIDEO_RENDERING_START:\n");
            _monitor.firstVideoFrameLatency = (int64_t)SDL_GetTickHR() - _monitor.prepareStartTick;
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerFirstVideoFrameRenderedNotification
             object:self];
            break;
        }
        case FFP_MSG_AUDIO_RENDERING_START: {
            NSLog(@"FFP_MSG_AUDIO_RENDERING_START:\n");
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerFirstAudioFrameRenderedNotification
             object:self];
            break;
        }
        case FFP_MSG_AUDIO_DECODED_START: {
            NSLog(@"FFP_MSG_AUDIO_DECODED_START:\n");
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerFirstAudioFrameDecodedNotification
             object:self];
            break;
        }
        case FFP_MSG_VIDEO_DECODED_START: {
            NSLog(@"FFP_MSG_VIDEO_DECODED_START:\n");
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerFirstVideoFrameDecodedNotification
             object:self];
            break;
        }
        case FFP_MSG_OPEN_INPUT: {
            NSLog(@"FFP_MSG_OPEN_INPUT:\n");
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerOpenInputNotification
             object:self];
            break;
        }
        case FFP_MSG_FIND_STREAM_INFO: {
            NSLog(@"FFP_MSG_FIND_STREAM_INFO:\n");
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerFindStreamInfoNotification
             object:self];
            break;
        }
        case FFP_MSG_COMPONENT_OPEN: {
            NSLog(@"FFP_MSG_COMPONENT_OPEN:\n");
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerComponentOpenNotification
             object:self];
            break;
        }
        case FFP_MSG_ACCURATE_SEEK_COMPLETE: {
            NSLog(@"FFP_MSG_ACCURATE_SEEK_COMPLETE:\n");
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerAccurateSeekCompleteNotification
             object:self
             userInfo:@{IJKMPMoviePlayerDidAccurateSeekCompleteCurPos: @(avmsg->arg1)}];
            break;
        }
        case FFP_MSG_VIDEO_SEEK_RENDERING_START: {
            NSLog(@"FFP_MSG_VIDEO_SEEK_RENDERING_START:\n");
            _isVideoSync = avmsg->arg1;
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerSeekVideoStartNotification
             object:self];
            _isVideoSync = 0;
            break;
        }
        case FFP_MSG_AUDIO_SEEK_RENDERING_START: {
            NSLog(@"FFP_MSG_AUDIO_SEEK_RENDERING_START:\n");
            _isAudioSync = avmsg->arg1;
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerSeekAudioStartNotification
             object:self];
            _isAudioSync = 0;
            break;
        }
        case FFP_MSG_FRAME_DROPPED: {
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerPlayFrameDroppedNotification
             object:self];
            break;
        }
        case FFP_MSG_FRAME_NOT_DROPPED: {
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerPlayFrameNotDroppedNotification
             object:self];
            break;
        }
        case FFP_MSG_VIDEO_RECORD_COMPLETE: {
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerVideoRecordCompleteNotification
             object:self
             userInfo:@{@"error": @(avmsg->arg1)}];

            if (self.inDownloadMode) {
                [self shutdown];
                if (self.onDownloadCompleted != NULL) {
                    self.onDownloadCompleted(avmsg->arg1);
                }
            }
            break;
        }
        case FFP_MSG_VIDEO_RECORD_START: {
            [[NSNotificationCenter defaultCenter]
             postNotificationName:IJKMPMoviePlayerVideoRecordStartNotification
             object:self];
            break;
        }
        default:
            // NSLog(@"unknown FFP_MSG_xxx(%d)\n", avmsg->what);
            break;
    }
    
    [_msgPool recycle:msg];
}

- (IJKFFMoviePlayerMessage *) obtainMessage {
    return [_msgPool obtain];
}

inline static IJKFFMoviePlayerController *ffplayerRetain(void *arg) {
    return (__bridge_transfer IJKFFMoviePlayerController *) arg;
}

int media_player_msg_loop(void* arg)
{
    @autoreleasepool {
        IjkMediaPlayer *mp = (IjkMediaPlayer*)arg;
        __weak IJKFFMoviePlayerController *ffpController = ffplayerRetain(ijkmp_set_weak_thiz(mp, NULL));
        while (ffpController) {
            @autoreleasepool {
                IJKFFMoviePlayerMessage *msg = [ffpController obtainMessage];
                if (!msg)
                    break;
                
                int retval = ijkmp_get_msg(mp, &msg->_msg, 1);
                if (retval < 0)
                    break;
                
                // block-get should never return 0
                assert(retval > 0);
                [ffpController performSelectorOnMainThread:@selector(postEvent:) withObject:msg waitUntilDone:NO];
            }
        }
        
        // retained in prepare_async, before SDL_CreateThreadEx
        ijkmp_dec_ref_p(&mp);
        return 0;
    }
}

static int64_t calculateElapsed(int64_t begin, int64_t end)
{
    if (begin <= 0)
        return -1;
    
    if (end < begin)
        return -1;
    
    return end - begin;
}

#pragma mark Airplay

-(BOOL)allowsMediaAirPlay
{
    if (!self)
        return NO;
    return _allowsMediaAirPlay;
}

-(void)setAllowsMediaAirPlay:(BOOL)b
{
    if (!self)
        return;
    _allowsMediaAirPlay = b;
}

-(BOOL)airPlayMediaActive
{
    if (!self)
        return NO;
    if (_isDanmakuMediaAirPlay) {
        return YES;
    }
    return NO;
}

-(BOOL)isDanmakuMediaAirPlay
{
    return _isDanmakuMediaAirPlay;
}

-(void)setIsDanmakuMediaAirPlay:(BOOL)isDanmakuMediaAirPlay
{
    _isDanmakuMediaAirPlay = isDanmakuMediaAirPlay;
    if (_isDanmakuMediaAirPlay) {
        _glView.scaleFactor = 1.0f;
    }
    else {
        CGFloat scale = [[UIScreen mainScreen] scale];
        if (scale < 0.1f)
            scale = 1.0f;
        _glView.scaleFactor = scale;
    }
    [[NSNotificationCenter defaultCenter] postNotificationName:IJKMPMoviePlayerIsAirPlayVideoActiveDidChangeNotification object:nil userInfo:nil];
}


#pragma mark Option Conventionce

- (void)setFormatOptionValue:(NSString *)value forKey:(NSString *)key
{
    [self setOptionValue:value forKey:key ofCategory:kIJKFFOptionCategoryFormat];
}

- (void)setCodecOptionValue:(NSString *)value forKey:(NSString *)key
{
    [self setOptionValue:value forKey:key ofCategory:kIJKFFOptionCategoryCodec];
}

- (void)setSwsOptionValue:(NSString *)value forKey:(NSString *)key
{
    [self setOptionValue:value forKey:key ofCategory:kIJKFFOptionCategorySws];
}

- (void)setPlayerOptionValue:(NSString *)value forKey:(NSString *)key
{
    [self setOptionValue:value forKey:key ofCategory:kIJKFFOptionCategoryPlayer];
}

- (void)setFormatOptionIntValue:(int64_t)value forKey:(NSString *)key
{
    [self setOptionIntValue:value forKey:key ofCategory:kIJKFFOptionCategoryFormat];
}

- (void)setCodecOptionIntValue:(int64_t)value forKey:(NSString *)key
{
    [self setOptionIntValue:value forKey:key ofCategory:kIJKFFOptionCategoryCodec];
}

- (void)setSwsOptionIntValue:(int64_t)value forKey:(NSString *)key
{
    [self setOptionIntValue:value forKey:key ofCategory:kIJKFFOptionCategorySws];
}

- (void)setPlayerOptionIntValue:(int64_t)value forKey:(NSString *)key
{
    [self setOptionIntValue:value forKey:key ofCategory:kIJKFFOptionCategoryPlayer];
}

- (void)setMaxBufferSize:(int)maxBufferSize
{
    [self setPlayerOptionIntValue:maxBufferSize forKey:@"max-buffer-size"];
}

#pragma mark app state changed

- (void)registerApplicationObservers
{
    [_notificationManager addObserver:self
                             selector:@selector(audioSessionInterrupt:)
                                 name:AVAudioSessionInterruptionNotification
                               object:nil];
    
    [_notificationManager addObserver:self
                             selector:@selector(applicationWillEnterForeground)
                                 name:UIApplicationWillEnterForegroundNotification
                               object:nil];
    
    [_notificationManager addObserver:self
                             selector:@selector(applicationDidBecomeActive)
                                 name:UIApplicationDidBecomeActiveNotification
                               object:nil];
    
    [_notificationManager addObserver:self
                             selector:@selector(applicationWillResignActive)
                                 name:UIApplicationWillResignActiveNotification
                               object:nil];
    
    [_notificationManager addObserver:self
                             selector:@selector(applicationDidEnterBackground)
                                 name:UIApplicationDidEnterBackgroundNotification
                               object:nil];
    
    [_notificationManager addObserver:self
                             selector:@selector(applicationWillTerminate)
                                 name:UIApplicationWillTerminateNotification
                               object:nil];
}

- (void)unregisterApplicationObservers
{
    [_notificationManager removeAllObservers:self];
}

- (void)audioSessionInterrupt:(NSNotification *)notification
{
    int reason = [[[notification userInfo] valueForKey:AVAudioSessionInterruptionTypeKey] intValue];
    switch (reason) {
        case AVAudioSessionInterruptionTypeBegan: {
            NSLog(@"IJKFFMoviePlayerController:audioSessionInterrupt: begin\n");
            switch (self.playbackState) {
                case IJKMPMoviePlaybackStatePaused:
                case IJKMPMoviePlaybackStateStopped:
                    _playingBeforeInterruption = NO;
                    break;
                default:
                    _playingBeforeInterruption = YES;
                    break;
            }
            [self pause];
            [[IJKAudioKit sharedInstance] setActive:NO];
            break;
        }
        case AVAudioSessionInterruptionTypeEnded: {
            NSLog(@"IJKFFMoviePlayerController:audioSessionInterrupt: end\n");
            [[IJKAudioKit sharedInstance] setActive:YES];
            if (_playingBeforeInterruption) {
                [self play];
            }
            break;
        }
    }
}

- (void)applicationWillEnterForeground
{
    NSLog(@"IJKFFMoviePlayerController:applicationWillEnterForeground: %d", (int)[UIApplication sharedApplication].applicationState);
}

- (void)applicationDidBecomeActive
{
    NSLog(@"IJKFFMoviePlayerController:applicationDidBecomeActive: %d", (int)[UIApplication sharedApplication].applicationState);
}

- (void)applicationWillResignActive
{
    NSLog(@"IJKFFMoviePlayerController:applicationWillResignActive: %d", (int)[UIApplication sharedApplication].applicationState);
    dispatch_async(dispatch_get_main_queue(), ^{
        if (_pauseInBackground) {
            [self pause];
        }
    });
}

- (void)applicationDidEnterBackground
{
    NSLog(@"IJKFFMoviePlayerController:applicationDidEnterBackground: %d", (int)[UIApplication sharedApplication].applicationState);
    dispatch_async(dispatch_get_main_queue(), ^{
        if (_pauseInBackground) {
            [self pause];
        }
    });
}

- (void)applicationWillTerminate
{
    NSLog(@"IJKFFMoviePlayerController:applicationWillTerminate: %d", (int)[UIApplication sharedApplication].applicationState);
    dispatch_async(dispatch_get_main_queue(), ^{
        if (_pauseInBackground) {
            [self pause];
        }
    });
}

#pragma mark IJKFFHudController
- (void)setHudValue:(NSString *)value forKey:(NSString *)key
{
    if ([[NSThread currentThread] isMainThread]) {
        [_hudViewController setHudValue:value forKey:key];
    } else {
        dispatch_async(dispatch_get_main_queue(), ^{
            [self setHudValue:value forKey:key];
        });
    }
}

- (ObjectTrackingInfoList) parseMetaData:(const unsigned char *)meta
{
    ObjectTrackingInfoList list;
    list.size = 0;

    NSData *nsMeta = [NSData dataWithBytes:meta length:strlen(meta)];
    NSDictionary *jsonDict = [NSJSONSerialization JSONObjectWithData:nsMeta options:NSJSONReadingMutableContainers error:nil];
    NSDictionary *jsonRoot = jsonDict[@"AGTX"];
    if (jsonRoot == nil) {
        jsonRoot = jsonDict[@"agtx"];
    }
    if (jsonRoot == nil) {
        jsonRoot = jsonDict[@"result"];
    }
    if (jsonRoot == nil) {
        return list;
    }
    NSDictionary *jsonIVA = jsonRoot[@"iva"];
    if (jsonIVA == nil) {
        return list;
    }

    NSArray *jsonOD = jsonIVA[@"od"];
    if (jsonOD != nil && jsonOD.count > 0) {
        for (int i = 0; i < jsonOD.count; i++) {
            NSDictionary *jsonItem = jsonOD[i];
            if (jsonItem == nil) {
                return list;
            }
            NSDictionary *jsonObj = jsonItem[@"obj"];
            if (jsonObj == nil) {
                return list;
            }
            NSArray *jsonRect = jsonObj[@"rect"];
            if (jsonRect == nil) {
                return list;
            }

            NSArray *jsonVector = jsonObj[@"vector"];
            if (jsonVector) {
                NSNumber *v = jsonVector[0];
                NSLog(@"nith1 vector %f", [v floatValue]);
                int vectorSize = MIN(jsonVector.count, FACE_EMBEDDING_LEN);
                list.info[list.size].vectorSize = vectorSize;
                for (int i = 0; i < vectorSize; i++) {
                    NSNumber *v = jsonVector[i];
                    list.info[list.size].vector[i] = [v floatValue];
                }
            }

            if (jsonRect.count >= 4 && list.size < MAX_OBJECT_TRACK) {
                CGRect r;
                r.origin.x = [jsonRect[0] intValue];
                r.origin.y = [jsonRect[1] intValue];
                r.size.width = [jsonRect[2] intValue] - r.origin.x;
                r.size.height = [jsonRect[3] intValue] - r.origin.y;
                list.info[list.size].rect = r;
                NSString *category = jsonObj[@"cat"];
                list.info[list.size].category = category;
                list.size++;
            }
        }
    } else {
        NSDictionary *jsonAROI = jsonIVA[@"aroi"];
        if (jsonAROI == nil) {
            return list;
        }
        NSDictionary *jsonROI = jsonAROI[@"roi"];
        if (jsonROI == nil) {
            return list;
        }
        NSArray *jsonRect = jsonROI[@"rect"];
        if (jsonRect == nil) {
            return list;
        }

        if (jsonRect.count >= 4) {
            CGRect r;
            r.origin.x = [jsonRect[0] intValue];
            r.origin.y = [jsonRect[1] intValue];
            r.size.width = [jsonRect[2] intValue] - r.origin.x;
            r.size.height = [jsonRect[3] intValue] - r.origin.y;
            list.info[list.size].rect = r;
            list.info[list.size].category = @"";
            list.size++;
        }
    }
    
    return list;
}

- (Frame *)RGBAFrame
{
    if (!_mediaPlayer) {
        return nil;
    }
    
    uint8_t *data = nil;
    uint8_t *meta = nil;
    int w, h;
    ijkmp_get_frame(_mediaPlayer, &data, &w, &h, &meta);
    if (data == nil) {
        return nil;
    }

    ObjectTrackingInfoList objTrackList;
    objTrackList.size = 0;
    if (meta != nil) {
        objTrackList = [self parseMetaData:meta];
    }

    int size = w * h * 4;
    
    NSData *nsData = [NSData dataWithBytes:data length:size];
    Frame *frame = [[Frame alloc] initFrame: nsData withWidth:w andHeight:h andObjTrackList:objTrackList];

    free(data);
    return frame;
}

- (AudioFrame *)AudioFrame
{
    if (!_mediaPlayer) {
        return nil;
    }

    uint8_t *data = nil;
    int size;
    int sampleRate;
    int channels;
    int bitsPerSample;
    ijkmp_get_audio(_mediaPlayer, &data, &size, &sampleRate, &channels, &bitsPerSample);
    if (data == nil) {
        return nil;
    }

    NSData *nsData = [NSData dataWithBytes:data length:size];
    AudioFrame *frame = [[AudioFrame alloc] initFrame: nsData withSampleRate: sampleRate withChannels:channels andBitsPerSample:bitsPerSample];

    free(data);
    return frame;
}

- (int) startVideoRecord:(NSString *)path
{
    return [self startVideoRecord:path withDuration:0];
}

- (int) startVideoRecord:(NSString *)path withDuration:(int)durationInSeconds
{
    self.inDownloadMode = NO;
    [self setPlayerOptionIntValue:durationInSeconds forKey:@"video-record-duration"];
    [self setPlayerOptionValue:path forKey:@"video-record-path"];
    return 0;
}

- (int) stopVideoRecord
{
    [self setPlayerOptionValue:@"" forKey:@"video-record-path"];
    return 0;
}

- (int) toMp4:(NSString *)path
andOnComplete:(void(^)(int))onComplete
{
    [self setPlayerOptionValue:path forKey:@"video-record-path"];
    [self setPlayerOptionIntValue:1 forKey:@"infbuf"];
    [self setPlayerOptionIntValue:0 forKey:@"volume"];
    self.onDownloadCompleted = onComplete;
    self.inDownloadMode = YES;
    self.shouldAutoplay = YES;
    [self prepareToPlay];
    return 0;
}

- (UIImage *) convertBitmapRGBA8ToUIImage:(const unsigned char *) buffer
                                withWidth:(int) width
                                andHeight:(int) height
{
    size_t bufferLength = width * height * 4;
    CGDataProviderRef provider = CGDataProviderCreateWithData(NULL, buffer, bufferLength, NULL);
    size_t bitsPerComponent = 8;
    size_t bitsPerPixel = 32;
    size_t bytesPerRow = 4 * width;
    
    CGColorSpaceRef colorSpaceRef = CGColorSpaceCreateDeviceRGB();
    if(colorSpaceRef == NULL) {
        NSLog(@"Error allocating color space");
        CGDataProviderRelease(provider);
        return nil;
    }
    
    CGBitmapInfo bitmapInfo = kCGBitmapByteOrderDefault | kCGImageAlphaPremultipliedLast;
    CGColorRenderingIntent renderingIntent = kCGRenderingIntentDefault;
    
    CGImageRef iref = CGImageCreate(width,
                height,
                bitsPerComponent,
                bitsPerPixel,
                bytesPerRow,
                colorSpaceRef,
                bitmapInfo,
                provider,    // data provider
                NULL,        // decode
                YES,            // should interpolate
                renderingIntent);
        
    uint32_t* pixels = (uint32_t*)malloc(bufferLength);
    
    if(pixels == NULL) {
        NSLog(@"Error: Memory not allocated for bitmap");
        CGDataProviderRelease(provider);
        CGColorSpaceRelease(colorSpaceRef);
        CGImageRelease(iref);
        return nil;
    }
    
    CGContextRef context = CGBitmapContextCreate(pixels,
                 width,
                 height,
                 bitsPerComponent,
                 bytesPerRow,
                 colorSpaceRef,
                 kCGImageAlphaPremultipliedLast);
    
    if(context == NULL) {
        NSLog(@"Error context not created");
        free(pixels);
    }
    
    UIImage *image = nil;
    if(context) {
        CGContextDrawImage(context, CGRectMake(0.0f, 0.0f, width, height), iref);
        CGImageRef imageRef = CGBitmapContextCreateImage(context);
        image = [UIImage imageWithCGImage:imageRef];
        CGImageRelease(imageRef);
        CGContextRelease(context);
    }
    
    CGColorSpaceRelease(colorSpaceRef);
    CGImageRelease(iref);
    CGDataProviderRelease(provider);
    
    if(pixels) {
        free(pixels);
    }
    return image;
}

- (UIImage *)cropImage:(UIImage *)imageToCrop
                toRect:(CGRect)rect
{
    CGImageRef imageRef = CGImageCreateWithImageInRect([imageToCrop CGImage], rect);
    UIImage *cropped = [UIImage imageWithCGImage:imageRef];
    CGImageRelease(imageRef);
    return cropped;
}

- (int)computeStep:(int) delta
{
    int step = delta;
    if (abs(delta) > MIN_DISTANCE) {
        step = (int)(delta * TRACKING_SPEED);
        if (step == 0) {
            if (delta < 0) {
                step = -1;
            } else {
                step = 1;
            }
        }
    }

    return step;
}

- (UIImage *)getSubImage:(UIImage *)image
              withCenter:(CGPoint)center
{
    int w = image.size.width;
    int h = image.size.height;
    int cx = center.x;
    int cy = center.y;
    int newW = w / 2;
    int newH = h / 2;
    int x = cx - newW / 2;
    int y = cy - newH / 2;
    
    if (x < 0) {
        x = 0;
    }
    if (y < 0) {
        y = 0;
    }
    
    int delta;
    if (x + newW >= w) {
        delta = x + newW - w + 1;
        x -= delta;
    }
    if (y + newH >= h) {
        delta = y + newH - h + 1;
        y -= delta;
    }
    
    if (self.currentX < 0 || self.currentY < 0) {
        self.currentX = w / 4;
        self.currentY = h / 4;
    }
    
    int deltaX = x - self.currentX;
    int deltaY = y - self.currentY;
    int stepX = [self computeStep:deltaX];
    int stepY = [self computeStep:deltaY];

    self.currentX += stepX;
    self.currentY += stepY;

    CGRect r;
    r.origin.x = self.currentX;
    r.origin.y = self.currentY;
    r.size.width = newW;
    r.size.height = newH;
    return [self cropImage:image toRect:r];
}

- (UIImage *) drawRect:(UIImage *)image
         withObjTrackList:(ObjectTrackingInfoList)objTrackList
               andMode:(Mode)mode
{
    UIGraphicsBeginImageContextWithOptions(image.size, NO, 0.0);
    [image drawInRect:CGRectMake(0.0, 0.0, image.size.width, image.size.height)];

    UIFont *font = [UIFont systemFontOfSize:30.f];
    CGContextRef context = UIGraphicsGetCurrentContext();
    for (int i = 0; i < objTrackList.size; i++) {
        CGRect rect = objTrackList.info[i].rect;
        UIColor *strokeColor = [UIColor redColor];
        [strokeColor set];
        CGContextSetLineWidth(context, 10.0f);
        CGContextAddRect(context, rect);
        CGContextDrawPath(context, kCGPathStroke);
        
        if (mode != OBJECT_DETECT) {
            break;
        }
                
        NSString *category = objTrackList.info[i].category;
        if (category != nil) {
            NSAttributedString *text = [[NSAttributedString alloc] initWithString : category
                                  attributes : @{
                         NSFontAttributeName : font,
              NSForegroundColorAttributeName : strokeColor }];
            CGPoint p = rect.origin;
            p.y -= 40.f;
            [text drawAtPoint:p];
        }
    }
    UIImage *newImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    return newImage;
}

- (int) draw:(Frame *)frame
 withMainView:(UIImageView *)mainView
   andSubView:(UIImageView *)subView
      andMode:(Mode)mode
{
    if (frame == nil || mainView == nil || (mode == PIP && subView == nil)) {
        return -1;
    }
    
    if (frame.objTrackList.size > 0) {
        self.objTrackList = frame.objTrackList;
        self.lastFoundObjectTime = [[NSDate date] timeIntervalSince1970];
    }
    
    UIImage *full = [self convertBitmapRGBA8ToUIImage:frame.pixels.bytes withWidth:frame.width andHeight:frame.height];
    
    if (self.lastFoundObjectTime < 0 || [[NSDate date] timeIntervalSince1970] - self.lastFoundObjectTime > TRACKING_THRESHOLD_IN_SECONDS) {
        [mainView setImage:full];
        subView.hidden = TRUE;
        return 0;
    }

    CGRect roi = self.objTrackList.info[0].rect;
    CGPoint center;
    if (self.objTrackList.size > 0) {
        center.x = roi.origin.x + roi.size.width / 2;
        center.y = roi.origin.y + roi.size.height / 2;
    } else {
        center.x = frame.width / 2;
        center.y = frame.height / 2;
    }

    UIImage *sub = [self getSubImage:full withCenter:center];
    
    if (mode == EPAN) {
        [mainView setImage:sub];
        subView.hidden = TRUE;
    } else if (mode == PIP) {
        [mainView setImage:sub];
        UIImage *fullWithRect = [self drawRect:full withObjTrackList:self.objTrackList andMode:mode];
        [subView setImage:fullWithRect];
        subView.hidden = FALSE;
    } else if (mode == OBJECT_DETECT) {
        UIImage *fullWithRect = [self drawRect:full withObjTrackList:self.objTrackList andMode:mode];
        [mainView setImage:fullWithRect];
        subView.hidden = TRUE;
    } else if (mode == NORMAL) {
        [mainView setImage:full];
        subView.hidden = TRUE;
    }
    
    return 0;
}

- (void) addPinchGesture
{
    UIPinchGestureRecognizer *pinchGesture = [[UIPinchGestureRecognizer alloc] initWithTarget: self
                                                                                       action: @selector(handlePinch:)];
    [_glView addGestureRecognizer: pinchGesture];
}

- (void) handlePinch: (UIPinchGestureRecognizer *)gesture
{
    CGFloat scale = self.baseScale * gesture.scale;
    if (scale < self.minScale) {
        scale = self.minScale;
    } else if (scale > self.maxScale) {
        scale = self.maxScale;
    }

    CGPoint offset = self.lastPoint;
    if (scale < self.baseScale) {
        offset.x = self.lastPoint.x * (scale - 1.0f) / (self.baseScale - 1.0f);
        offset.y = self.lastPoint.y * (scale - 1.0f) / (self.baseScale - 1.0f);
    }    

    CGAffineTransform scaleTransform = CGAffineTransformMakeScale(scale, scale);
    CGAffineTransform move = CGAffineTransformMakeTranslation(offset.x, offset.y);
    _glView.transform = CGAffineTransformConcat(scaleTransform, move);

    if (gesture.state == UIGestureRecognizerStateEnded) {
        self.baseScale = scale;
        self.lastPoint = offset;
    }
}

- (void) addPanGesture
{
    UIPanGestureRecognizer *panGesture = [[UIPanGestureRecognizer alloc] initWithTarget: self
                                                                                 action: @selector(handlePan:)];
    [_glView addGestureRecognizer: panGesture];
}

- (void) handlePan: (UIPanGestureRecognizer *)gesture
{
    NSUInteger numberOfTouches = gesture.numberOfTouches;
    if (numberOfTouches == 1)
    {
        static CGPoint currentPoint = {0.0, 0.0};
        CGFloat w = _glView.frame.size.width / self.baseScale;
        CGFloat h = _glView.frame.size.height / self.baseScale;
        self.frameSize = CGSizeMake(w, h);

        if (gesture.state == UIGestureRecognizerStateBegan)
        {
            currentPoint = self.lastPoint;
        }

        CGPoint translation = [gesture translationInView: gesture.view];

        float speed = 1.0;
        self.lastPoint = CGPointMake(translation.x * speed * self.baseScale + currentPoint.x,
                                     translation.y * speed * self.baseScale + currentPoint.y);
        
        CGFloat maxX = ((self.baseScale - 1.0f) / 2.0f) * _frameSize.width;
        CGFloat maxY = ((self.baseScale - 1.0f) / 2.0f) * _frameSize.height;
        CGFloat x = MAX(-maxX, MIN(self.lastPoint.x, maxX));
        CGFloat y = MAX(-maxY, MIN(self.lastPoint.y, maxY));
        self.lastPoint = CGPointMake(x, y);

        CGAffineTransform scale = CGAffineTransformMakeScale(self.baseScale, self.baseScale);
        CGAffineTransform move = CGAffineTransformMakeTranslation(self.lastPoint.x, self.lastPoint.y);
        _glView.transform = CGAffineTransformConcat(scale, move);
    }
    
    if (gesture.state == UIGestureRecognizerStateEnded) {
        if (self.onFling) {
            CGPoint t = [gesture translationInView: gesture.view];
            CGPoint v = [gesture velocityInView:gesture.view];
            self.onFling(t, v);
        }
    }
}

- (void) resetView
{
    dispatch_async(dispatch_get_main_queue(), ^{
        self.minScale = 1.0f;
        self.maxScale = 3.0f;
        self.baseScale = 1.0f;
        self.lastPoint = CGPointMake(0, 0);
        _glView.transform = CGAffineTransformIdentity;
    });
}

- (void) setWebRTCMic:(BOOL)enable {
    if (_client != NULL) {
        [_client setMicEnable:enable];
    }
}

- (NSDictionary *)getPlaybackBarEvents:(int)startTime {
    return [_client getPlaybackBarEvents:startTime];
}

- (NSDictionary *)getPlaybackAllEvents:(int)startTime {
    return [_client getPlaybackAllEvents:startTime];
}

- (NSDictionary *)startSpeaker {
    return [_client startSpeaker];
}

- (NSDictionary *)stopSpeaker {
    return [_client stopSpeaker];
}


@end

