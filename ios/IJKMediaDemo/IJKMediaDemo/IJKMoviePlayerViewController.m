/*
 * Copyright (C) 2013-2015 Bilibili
 * Copyright (C) 2013-2015 Zhang Rui <bbcallen@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#import "AVAPIs.h"
#import "IOTCAPIs.h"
#import "NebulaAPIs.h"
#import "TUTKGlobalAPIs.h"
#import "IOTCGlobalLock.h"
#import "OneCamFramework/AVAPI3_interface.h"
#import "OneCamFramework/AVAPI4_interface.h"
#import "IJKMoviePlayerViewController.h"
#import "IJKMediaControl.h"
#import "IJKCommon.h"
#import "IJKDemoHistory.h"
#import "OneCamFramework/Nebula_interface.h"

const AVAPI3 avAPI3s = {
    .size = sizeof(AVAPI3),
    .ClientStartEx = avClientStartEx,
    .ClientStop = avClientStop,
    .SendIOCtrl = avSendIOCtrl,
    .RecvIOCtrl = avRecvIOCtrl,
    .RecvAudioData = avRecvAudioData,
    .RecvFrameData2 = avRecvFrameData2,
    .GlobalLock = IOTC_GlobalLock_Lock,
    .GlobalUnlock = IOTC_GlobalLock_Unlock,
};

const AVAPI4 avAPIs = {
    .size = sizeof(AVAPI4),
    .ClientStartEx = avClientStartEx,
    .ClientStop = avClientStop,
    .SendJSONCtrlRequest = avSendJSONCtrlRequest,
    .FreeJSONCtrlResponse = avFreeJSONCtrlResponse,
    .RecvAudioData = avRecvAudioData,
    .RecvFrameData2 = avRecvFrameData2,
};

static int Client_New(const char *udid, const char *credential, long *ctx) {
    NebulaClientCtx *nebulaCtx;
    int ret = Nebula_Client_New_From_String(udid, credential, &nebulaCtx);
    *ctx = (long)nebulaCtx;
    return ret;
}

static int Send_Command(long ctx, const char *req, char **response, int timeoutMS) {
    NebulaJsonObject *nebulaResponse;
    int ret = Nebula_Client_Send_Command((NebulaClientCtx *)ctx, req, &nebulaResponse, timeoutMS, nil);
    if (ret >= 0) {
        *response = (char *)Nebula_Json_Obj_To_String(nebulaResponse);
    } else {
        *response = nil;
    }
    return ret;
}

NebulaAPI nebulaAPIs = {
    .ctx = 0,
    .size = sizeof(NebulaAPI),
    .Send_Command = Send_Command
};

void nebulaClientConnectStateFn(NebulaClientCtx *client, NebulaDeviceLoginState state) {
}

static const bool DEMO_VIDEO_RECORD = false;
static const bool DEMO_AVAPI3 = false;
static const bool DEMO_AVAPI4 = false;
static const bool DEMO_WEBRTC = true;
static const bool DEMO_OBJECT_TRACKING = false;
static const bool DEMO_TOMP4 = false;
static const bool DEMO_SCALE_GESTURE = false;
static const char *IOTC_LICENSE_KEY = "AQAAAKeAQF10XxnkUkxKdDAMTxlxOfQJTIdZeHzBh790eOZgzQon8X3ISVznSM3l0ucpTWvSXO+lQvdJUFFfKcDc2bHv95gXmFp1ZPEBbbyPO26Jjdg68KHdCnPRRtJFQZqPvogJCaW1o71C/VndP/NbvZbhUfQfiX8f2GusXa10fBqiavArv3SdiOZtJEbvzdLyltPHeh3t0NGskhKQGvAnOZQb";
static const char *AVAPI3_UID = "your_uid";
static const char *AVAPI3_ACCOUNT = "your_account";
static const char *AVAPI3_PASSWORD = "your_password";
static const int AVAPI_CHANNEL = 0;
static const char *AVAPI4_UDID = "your_udid";
static const char *AVAPI4_CREDENTIAL = "your_credential";
static const char *AVAPI4_DMTOKEN = "your_dmtoken";
static const char *AVAPI4_REALM = "your_realm";
static const char *AVAPI4_FILENAME = "20200518013511";
static const char *WEBRTC_UDID = "2B28RITAZWZFYCAZVJL0WVUCP4JOMIOJCKN4WRAJ";
static const char *WEBRTC_CREDENTIAL = "qh6CWY73ELHgvCc1oGWUrA0wTTCTdyBiMocp17AUmbLrp/k+IXJE3xpwMP8UVQzcNoTcGz+sGY+Bv/49MzG+rAK0+XMlwv5MZIdY4yM0Zmqxgnh3RiKeJBfkKFjXF6fgPnBuGUyl5YpO8KKLrBip2R9HILsFExkupDHdwzHnuNsyuLUhaw19G31vEGKnko4qb8WItXGecxvNkM2t5QASRw==";
static const char *WEBRTC_DMTOKEN = "Oz7WSSkb1fDcFcS9DcZuCvqrDdZZWUCULJEznVkm9xo=";
static const char *WEBRTC_REALM = "56ai";
static const int WEBRTC_CHANNEL = IJK_NOCHANNEL_VALUE;
static const int WEBRTC_EVENT_START_TIME = IJK_NOEVENT_VALUE;// 1684560600;//IJK_NOEVENT_VALUE; //1684585000

//
// <INFO>: If live url channel is not 0, need to add account, password, and session-id parameters to url.
//
static const char *AVAPI3_LIVE_URL="avapi://tutk.com/live?av-index=%d";

static const unsigned int AVAPI3_START_TIME = 1580882907;
static const char *AVAPI3_PLAYBACK_URL="avapi://tutk.com/playback?session-id=%d&channel=%d&account=%s&password=%s&start-time=%d&av-index=%d";

static const char *AVAPI4_LIVE_URL="avapi://tutk.com/live?av-index=%d";
static const char *AVAPI4_PLAYBACK_URL="avapi://tutk.com/playback?session-id=%d&channel=%d&filename=%s&av-index=%d";
static const char *WEBRTC_LIVE_URL="webrtc://tutk.com?pc_id=%ld";
static const char *WEBRTC_PLAYBACK_URL="webrtc://tutk.com?pc_id=%ld&duration-ms=%d";

void loginCallback(NebulaClientCtx *client, NebulaDeviceLoginState state)
{
}

static NebulaClientCtx *clientCtx;

@implementation IJKVideoViewController

- (void)dealloc
{
}

+ (void)presentFromViewController:(UIViewController *)viewController withTitle:(NSString *)title URL:(NSURL *)url completion:(void (^)())completion {
    IJKDemoHistoryItem *historyItem = [[IJKDemoHistoryItem alloc] init];
    
    historyItem.title = title;
    historyItem.url = url;
    [[IJKDemoHistory instance] add:historyItem];
    
    [viewController presentViewController:[[IJKVideoViewController alloc] initWithURL:url] animated:YES completion:completion];
}

- (instancetype)initWithURL:(NSURL *)url {
    self = [self initWithNibName:@"IJKMoviePlayerViewController" bundle:nil];
    if (self) {
        self.url = url;
    }
    return self;
}

#define EXPECTED_IJKPLAYER_VERSION (1 << 16) & 0xFF) | 
- (void)viewDidLoad
{
    [super viewDidLoad];
    [self installNebullaObserver];
#ifdef DEBUG
    [IJKFFMoviePlayerController setLogReport:YES];
    [IJKFFMoviePlayerController setLogLevel:k_IJK_LOG_DEBUG];
#else
    [IJKFFMoviePlayerController setLogReport:NO];
    [IJKFFMoviePlayerController setLogLevel:k_IJK_LOG_INFO];
#endif
        
    IJKFFOptions *options = [IJKFFOptions optionsByDefault];
    [options setOptionIntValue:1 forKey:@"videotoolbox" ofCategory:kIJKFFOptionCategoryPlayer];

    // Enable acoustic echo cancelling.
    // [options setOptionIntValue:1 forKey:@"enable-aec" ofCategory:kIJKFFOptionCategoryPlayer];

    if (DEMO_OBJECT_TRACKING) {
        [options setOptionIntValue:1 forKey:@"enable-get-frame" ofCategory:kIJKFFOptionCategoryPlayer];
    }

    TUTK_SDK_Set_License_Key(IOTC_LICENSE_KEY);
    if (DEMO_AVAPI3) {
        IOTC_Initialize2(0);
        avInitialize(3);
        self.sid = IOTC_Get_SessionID();
        IOTC_Connect_ByUID_Parallel(AVAPI3_UID, self.sid);
        AVClientStartInConfig avConfig;
        AVClientStartOutConfig avOutConfig;
        avConfig.cb = sizeof(AVClientStartInConfig);
        avOutConfig.cb = sizeof(AVClientStartOutConfig);
        avConfig.iotc_session_id = self.sid;
        avConfig.iotc_channel_id = AVAPI_CHANNEL;
        avConfig.resend = 1;
        avConfig.timeout_sec = 20;
        avConfig.auth_type = 0;
        avConfig.security_mode = 2;
        avConfig.account_or_identity = AVAPI3_ACCOUNT;
        avConfig.password_or_token = AVAPI3_PASSWORD;
        self.avIndex = avClientStartEx(&avConfig, &avOutConfig);
        [options setFormatOptionIntValue:(int64_t)&avAPI3s forKey:@"av_api3"];
        [options setFormatOptionIntValue:2 forKey:@"dtls"];
        char url[512];
        sprintf(url, AVAPI3_PLAYBACK_URL, self.sid, AVAPI_CHANNEL, AVAPI3_ACCOUNT, AVAPI3_PASSWORD, AVAPI3_START_TIME, self.avIndex);
        sprintf(url, AVAPI3_LIVE_URL, self.avIndex);
        self.url = [NSURL URLWithString:@(url)];
    }
    if (DEMO_AVAPI4) {
        TUTK_SDK_Set_Region(REGION_US);
        IOTC_Initialize2(0);
        avInitialize(100);
        Nebula_Initialize();
        
        Nebula_Client_New_From_String(AVAPI4_UDID, AVAPI4_CREDENTIAL, &clientCtx);
        self.sid = IOTC_Client_Connect_By_Nebula(clientCtx, AVAPI4_DMTOKEN, AVAPI4_REALM, 30000, NULL);
        
        AVClientStartInConfig avConfig;
        AVClientStartOutConfig avOutConfig;
        avConfig.cb = sizeof(AVClientStartInConfig);
        avOutConfig.cb = sizeof(AVClientStartOutConfig);
        avConfig.iotc_session_id = self.sid;
        avConfig.iotc_channel_id = AVAPI_CHANNEL;
        avConfig.resend = 1;
        avConfig.auth_type = 2;
        avConfig.security_mode = 2;
        avConfig.timeout_sec = 20;
        avConfig.account_or_identity = "";
        avConfig.password_or_token = "";
        self.avIndex = avClientStartEx(&avConfig, &avOutConfig);
                
        [options setFormatOptionIntValue:(int64_t)&avAPIs forKey:@"av_api4"];
        [options setFormatOptionIntValue:(int64_t)&avAPIs forKey:@"avapidec_av_api4"];
        [options setFormatOptionIntValue:2 forKey:@"dtls"];
        char url[512];
        sprintf(url, AVAPI4_PLAYBACK_URL, self.sid, 1, AVAPI4_FILENAME, self.avIndex);
        sprintf(url, AVAPI4_LIVE_URL, self.avIndex);
        self.url = [NSURL URLWithString:@(url)];
    }
    if (DEMO_WEBRTC) {
        Nebula_Initialize();
        NebulaClientCtx *ctx;
        Nebula_Client_New_From_String(WEBRTC_UDID, WEBRTC_CREDENTIAL, &ctx);
        Nebula_Client_Connect(ctx, nebulaClientConnectStateFn, 10000, NULL);
        nebulaAPIs.ctx = (long)ctx;
        self.player = [[IJKFFMoviePlayerController alloc] initWithOptions:options];

        long webrtc_id = [self.player startWebRTC:@(WEBRTC_DMTOKEN)
                                    andRealm:@(WEBRTC_REALM)
                                     andNebulaAPI:&nebulaAPIs
                                    andStreamType:IJKStreamTypeAudioAndVideo
                                     andStartTime:WEBRTC_EVENT_START_TIME
                                      andFileName:@"x1_ravi"
                                     andChannelId:WEBRTC_CHANNEL
                                andIsQuickConnect:true];
        if (webrtc_id == INVALID_WEBRTC_ID) {
            NSLog(@"start webrtc failed!");
            return;
        }
        char url[512];
        int eventDurationMS = 10000;
        sprintf(url, WEBRTC_PLAYBACK_URL, webrtc_id, eventDurationMS);
        sprintf(url, WEBRTC_LIVE_URL, webrtc_id);
        [self.player setVideoPath:@(url)];
        [self.player setWebRTCMic:YES];
    }else {
        self.player = [[IJKFFMoviePlayerController alloc] initWithContentURL:self.url withOptions:options];
    }

    if (DEMO_TOMP4) {
        NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
        NSString *filePath = [[paths objectAtIndex:0] stringByAppendingPathComponent:@"out.mp4"];
        [self.player toMp4:filePath andOnComplete:^(int result){
            if (result == 0) {
                NSLog(@"download success");
            } else {
                NSLog(@"download fail");
            }
        }];
        return;
    }

    self.player.view.autoresizingMask = UIViewAutoresizingFlexibleWidth|UIViewAutoresizingFlexibleHeight;
    self.player.view.frame = self.view.bounds;
    self.player.scalingMode = IJKMPMovieScalingModeAspectFit;
    self.player.shouldAutoplay = YES;
    
    self.view.autoresizesSubviews = YES;
    [self.view addSubview:self.player.view];
    if (!DEMO_SCALE_GESTURE) {
        [self.view addSubview:self.mediaControl];
    }
    
    self.mediaControl.delegatePlayer = self.player;
    
    if (DEMO_OBJECT_TRACKING) {
        self.image = [[UIImageView alloc] initWithFrame: self.view.bounds];
        self.image.autoresizingMask = UIViewAutoresizingFlexibleWidth|UIViewAutoresizingFlexibleHeight;
        [self.image setContentMode:UIViewContentModeScaleToFill];
        [self.view addSubview:self.image];
        self.subImage = [[UIImageView alloc] initWithFrame: CGRectMake(0,0,320,180)];
        [self.view addSubview:self.subImage];
    }
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    
    [self installMovieNotificationObservers];
    
    [self.player prepareToPlay];
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    
    if (self.timer) {
        [self.timer invalidate];
        self.timer = nil;
    }

    [self.player shutdown];
    [self removeMovieNotificationObservers];
    if (DEMO_AVAPI3) {
        avClientStop(self.avIndex);
        IOTC_Session_Close(self.sid);
    }
    if (DEMO_AVAPI4) {
        Nebula_Client_Delete(clientCtx);
        avClientStop(self.avIndex);
        IOTC_Session_Close(self.sid);
    }
}

- (BOOL)shouldAutorotateToInterfaceOrientation:(UIInterfaceOrientation)toInterfaceOrientation{
    return UIInterfaceOrientationIsLandscape(toInterfaceOrientation);
}

- (UIInterfaceOrientationMask)supportedInterfaceOrientations
{
    return UIInterfaceOrientationMaskLandscape;
}

- (void)didReceiveMemoryWarning
{
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

#pragma mark IBAction

- (IBAction)onClickMediaControl:(id)sender
{
    [self.mediaControl showAndFade];
}

- (IBAction)onClickOverlay:(id)sender
{
    [self.mediaControl hide];
}

- (IBAction)onClickDone:(id)sender
{
    [self.presentingViewController dismissViewControllerAnimated:YES completion:nil];
}

- (IBAction)onClickHUD:(UIBarButtonItem *)sender
{
    if ([self.player isKindOfClass:[IJKFFMoviePlayerController class]]) {
        IJKFFMoviePlayerController *player = self.player;
        player.shouldShowHudView = !player.shouldShowHudView;
        
        sender.title = (player.shouldShowHudView ? @"HUD On" : @"HUD Off");
    }
}

- (IBAction)onClickPlay:(id)sender
{
    [self.player play];
    [self.mediaControl refreshMediaControl];
}

- (IBAction)onClickPause:(id)sender
{
    [self.player pause];
    [self.mediaControl refreshMediaControl];
}

- (IBAction)didSliderTouchDown
{
    [self.mediaControl beginDragMediaSlider];
}

- (IBAction)didSliderTouchCancel
{
    [self.mediaControl endDragMediaSlider];
}

- (IBAction)didSliderTouchUpOutside
{
    [self.mediaControl endDragMediaSlider];
}

- (IBAction)didSliderTouchUpInside
{
    self.player.currentPlaybackTime = self.mediaControl.mediaProgressSlider.value;
    [self.mediaControl endDragMediaSlider];
}

- (IBAction)didSliderValueChanged
{
    [self.mediaControl continueDragMediaSlider];
}

- (void)loadStateDidChange:(NSNotification*)notification
{
    //    MPMovieLoadStateUnknown        = 0,
    //    MPMovieLoadStatePlayable       = 1 << 0,
    //    MPMovieLoadStatePlaythroughOK  = 1 << 1, // Playback will be automatically started in this state when shouldAutoplay is YES
    //    MPMovieLoadStateStalled        = 1 << 2, // Playback will be automatically paused in this state, if started
    
    IJKMPMovieLoadState loadState = _player.loadState;
    
    if ((loadState & IJKMPMovieLoadStatePlaythroughOK) != 0) {
        NSLog(@"loadStateDidChange: IJKMPMovieLoadStatePlaythroughOK: %d\n", (int)loadState);
    } else if ((loadState & IJKMPMovieLoadStateStalled) != 0) {
        NSLog(@"loadStateDidChange: IJKMPMovieLoadStateStalled: %d\n", (int)loadState);
    } else {
        NSLog(@"loadStateDidChange: ???: %d\n", (int)loadState);
    }
}

- (void)moviePlayBackDidFinish:(NSNotification*)notification
{
    int error;
    int reason = [[[notification userInfo] valueForKey:IJKMPMoviePlayerPlaybackDidFinishReasonUserInfoKey] intValue];
    
    switch (reason)
    {
        case IJKMPMovieFinishReasonPlaybackEnded:
            NSLog(@"playbackStateDidChange: IJKMPMovieFinishReasonPlaybackEnded: %d\n", reason);
            break;
            
        case IJKMPMovieFinishReasonUserExited:
            NSLog(@"playbackStateDidChange: IJKMPMovieFinishReasonUserExited: %d\n", reason);
            break;
            
        case IJKMPMovieFinishReasonPlaybackError:
            error = [[[notification userInfo] valueForKey:@"error"] intValue];
            NSLog(@"playbackStateDidChange: IJKMPMovieFinishReasonPlaybackError: %d\n", error);
            if (error == ERROR_HTTP_UNAUTHORIZED) {
                // do something for 401 error
            }
            break;
            
        default:
            NSLog(@"playbackPlayBackDidFinish: ???: %d\n", reason);
            break;
    }
}

- (void)mediaIsPreparedToPlayDidChange:(NSNotification*)notification
{
    NSLog(@"mediaIsPreparedToPlayDidChange\n");
    
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *filePath = [[paths objectAtIndex:0] stringByAppendingPathComponent:@"record.mp4"];
    //
    // <INFO>: video record only works well for rtsp source
    //
    if (DEMO_VIDEO_RECORD) {
        [self.player startVideoRecord:filePath];
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^(){
            sleep(10);
            [self.player stopVideoRecord];
        });
    }
        
    if (DEMO_OBJECT_TRACKING) {
        self.timer = [NSTimer scheduledTimerWithTimeInterval: 0.05
                                         target: self
                                       selector: @selector(updateFrame:)
                                       userInfo: nil
                                        repeats: YES];

    }
}

-(void)updateFrame: (NSTimer *)timer
{
    Frame *frame = _player.RGBAFrame;
    [self.player draw:frame withMainView:self.image andSubView:self.subImage andMode:PIP];
}

- (void)moviePlayBackStateDidChange:(NSNotification*)notification
{
    switch (_player.playbackState)
    {
        case IJKMPMoviePlaybackStateStopped: {
            NSLog(@"IJKMPMoviePlayBackStateDidChange %d: stoped", (int)_player.playbackState);
            break;
        }
        case IJKMPMoviePlaybackStatePlaying: {
            NSLog(@"IJKMPMoviePlayBackStateDidChange %d: playing", (int)_player.playbackState);
            break;
        }
        case IJKMPMoviePlaybackStatePaused: {
            NSLog(@"IJKMPMoviePlayBackStateDidChange %d: paused", (int)_player.playbackState);
            break;
        }
        case IJKMPMoviePlaybackStateInterrupted: {
            NSLog(@"IJKMPMoviePlayBackStateDidChange %d: interrupted", (int)_player.playbackState);
            break;
        }
        case IJKMPMoviePlaybackStateSeekingForward:
        case IJKMPMoviePlaybackStateSeekingBackward: {
            NSLog(@"IJKMPMoviePlayBackStateDidChange %d: seeking", (int)_player.playbackState);
            break;
        }
        default: {
            NSLog(@"IJKMPMoviePlayBackStateDidChange %d: unknown", (int)_player.playbackState);
            break;
        }
    }
}

- (void)movieFrameDropped:(NSNotification*)notification
{
    NSLog(@"movieFrameDropped");
}

- (void)movieFrameNotDropped:(NSNotification*)notification
{
    NSLog(@"movieFrameNotDropped");
}

- (void)videoRecordComplete:(NSNotification*)notification
{
    int error = [[[notification userInfo] valueForKey:@"error"] intValue];

    if (error == 0) {
        NSLog(@"videoRecordComplete success");
    } else {
        NSLog(@"videoRecordComplete fail");
    }
}

- (void)videoRecordStart:(NSNotification*)notification
{
    NSLog(@"videoRecordStart");
}
- (void)testing:(NSNotification*)notification
{
    NSDictionary *userInfo = notification.userInfo;
    NSLog(@"%@", userInfo[@"error"]);
    NSLog(@"Museer camera offline");
}
- (void)installNebullaObserver {
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(testing:)
                                                 name:@"didRecieveNebulaResponse" object:NULL];
}

#pragma mark Install Movie Notifications

/* Register observers for the various movie object notifications. */
-(void)installMovieNotificationObservers
{
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(loadStateDidChange:)
                                                 name:IJKMPMoviePlayerLoadStateDidChangeNotification
                                               object:_player];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(moviePlayBackDidFinish:)
                                                 name:IJKMPMoviePlayerPlaybackDidFinishNotification
                                               object:_player];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(mediaIsPreparedToPlayDidChange:)
                                                 name:IJKMPMediaPlaybackIsPreparedToPlayDidChangeNotification
                                               object:_player];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(moviePlayBackStateDidChange:)
                                                 name:IJKMPMoviePlayerPlaybackStateDidChangeNotification
                                               object:_player];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(movieFrameDropped:)
                                                 name:IJKMPMoviePlayerPlayFrameDroppedNotification
                                               object:_player];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(movieFrameNotDropped:)
                                                 name:IJKMPMoviePlayerPlayFrameNotDroppedNotification
                                               object:_player];

    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(videoRecordComplete:)
                                                 name:IJKMPMoviePlayerVideoRecordCompleteNotification
                                               object:_player];

    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(videoRecordStart:)
                                                 name:IJKMPMoviePlayerVideoRecordStartNotification
                                               object:_player];

}

#pragma mark Remove Movie Notification Handlers

/* Remove the movie notification observers from the movie object. */
-(void)removeMovieNotificationObservers
{
    [[NSNotificationCenter defaultCenter]removeObserver:self name:IJKMPMoviePlayerLoadStateDidChangeNotification object:_player];
    [[NSNotificationCenter defaultCenter]removeObserver:self name:IJKMPMoviePlayerPlaybackDidFinishNotification object:_player];
    [[NSNotificationCenter defaultCenter]removeObserver:self name:IJKMPMediaPlaybackIsPreparedToPlayDidChangeNotification object:_player];
    [[NSNotificationCenter defaultCenter]removeObserver:self name:IJKMPMoviePlayerPlaybackStateDidChangeNotification object:_player];
    [[NSNotificationCenter defaultCenter]removeObserver:self name:IJKMPMoviePlayerPlayFrameDroppedNotification object:_player];
    [[NSNotificationCenter defaultCenter]removeObserver:self name:IJKMPMoviePlayerPlayFrameNotDroppedNotification object:_player];
}

@end
