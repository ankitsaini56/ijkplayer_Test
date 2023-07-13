/*
 * IJKMediaPlayback.h
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

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import "Nebula_interface.h"

#define MAX_OBJECT_TRACK 10
#define IJK_NOEVENT_VALUE -1
#define IJK_NOCHANNEL_VALUE -1
#define INVALID_WEBRTC_ID 0
#define FACE_EMBEDDING_LEN 128

#define MKTAG(a,b,c,d) ((a) | ((b) << 8) | ((c) << 16) | ((unsigned)(d) << 24))
#define ERRTAG(a, b, c, d) (-(int)MKTAG(a, b, c, d))

//
// <INFO>: swift define need to be constant
//
#define ERROR_HTTP_UNAUTHORIZED 0xcecfcb08 // ERRTAG(0xF8,'4','0','1')

typedef NS_ENUM(NSInteger, IJKMPMovieScalingMode) {
    IJKMPMovieScalingModeNone,       // No scaling
    IJKMPMovieScalingModeAspectFit,  // Uniform scale until one dimension fits
    IJKMPMovieScalingModeAspectFill, // Uniform scale until the movie fills the visible bounds. One dimension may have clipped contents
    IJKMPMovieScalingModeFill        // Non-uniform scale. Both render dimensions will exactly match the visible bounds
};

typedef NS_ENUM(NSInteger, IJKMPMoviePlaybackState) {
    IJKMPMoviePlaybackStateStopped,
    IJKMPMoviePlaybackStatePlaying,
    IJKMPMoviePlaybackStatePaused,
    IJKMPMoviePlaybackStateInterrupted,
    IJKMPMoviePlaybackStateSeekingForward,
    IJKMPMoviePlaybackStateSeekingBackward
};

typedef NS_OPTIONS(NSUInteger, IJKMPMovieLoadState) {
    IJKMPMovieLoadStateUnknown        = 0,
    IJKMPMovieLoadStatePlayable       = 1 << 0,
    IJKMPMovieLoadStatePlaythroughOK  = 1 << 1, // Playback will be automatically started in this state when shouldAutoplay is YES
    IJKMPMovieLoadStateStalled        = 1 << 2, // Playback will be automatically paused in this state, if started
};

typedef NS_ENUM(NSInteger, IJKMPMovieFinishReason) {
    IJKMPMovieFinishReasonPlaybackEnded,
    IJKMPMovieFinishReasonPlaybackError,
    IJKMPMovieFinishReasonUserExited
};

// -----------------------------------------------------------------------------
// Thumbnails

typedef NS_ENUM(NSInteger, IJKMPMovieTimeOption) {
    IJKMPMovieTimeOptionNearestKeyFrame,
    IJKMPMovieTimeOptionExact
};

typedef NS_ENUM(NSInteger, Mode) {
    EPAN,
    PIP,
    OBJECT_DETECT,
    NORMAL
};

typedef void (*OnFling)(float translationX, float translationY, float velocityX, float velocityY);

@interface ObjectTrackingInfo : NSObject

@property(nonatomic, readonly) CGRect rect;
@property(nonatomic, readonly) NSString *category;
@property(nonatomic, readonly) NSArray *vector;

- (instancetype)initObjectTrackingInfo:(CGRect)rect
                         withCategory:(NSString *)category
                            andVector:(NSArray *)vector;

@end


@interface Frame : NSObject

@property(nonatomic, readonly) NSData *pixels;
@property(nonatomic, readonly) int width;
@property(nonatomic, readonly) int height;
@property(nonatomic, readonly) NSArray *objTrackList;

- (instancetype)initFrame:(NSData *)pixels
                withWidth:(int)w
                andHeight:(int)h
                andObjTrackList:(NSArray *)objTrackList;

@end

@interface AudioFrame : NSObject

@property(nonatomic, readonly) NSData *data;
@property(nonatomic, readonly) int sampleRate;
@property(nonatomic, readonly) int channels;
@property(nonatomic, readonly) int bitsPerSample;

- (instancetype)initFrame:(NSData *)data
            withSampleRate:(int)sampleRate
              withChannels:(int)channels
         andBitsPerSample:(int)bitsPerSample;

@end

@protocol IJKMediaPlayback;

#pragma mark IJKMediaPlayback

@protocol IJKMediaPlayback <NSObject>

- (void)prepareToPlay;
- (void)play;
- (void)pause;
- (void)stop;
- (BOOL)isPlaying;
- (void)shutdown;
- (void)shutdown:(BOOL)async;
- (void)setPauseInBackground:(BOOL)pause;
- (int) startVideoRecord:(NSString *)path;
- (int) startVideoRecord:(NSString *)path
            withDuration:(int)durationInSeconds;
- (int) stopVideoRecord;
- (int) toMp4:(NSString *)path
         andOnComplete: ( void ( ^ )( int ) )onComplete;

- (int) draw:(Frame *)frame
withMainView:(UIImageView *)mainView
  andSubView:(UIImageView *)subView
     andMode:(Mode)mode;

- (int64_t)videoFrameTimestamp;
- (float)videoDecodeFramesPerSecond;
- (float)videoOutputFramesPerSecond;
- (int64_t)videoBitRate;
- (int64_t)networkBitRate;
- (int64_t)videoCachedDuration;
- (int64_t)audioCachedDuration;
- (float)avdiff;
- (void)resetView;
- (void)setWebRTCMic:(BOOL)enable;

- (long)startWebRTC:(NSString *)dmToken
           withRealm:(NSString *)realm
      withNebulaAPI:(const NebulaAPI *)nebulaAPIs;

- (long)startWebRTC:(NSString *)dmToken
          andRealm:(NSString *)realm
       andNebulaAPI:(const NebulaAPI *)nebulaAPI
      andStreamType:(NSString *)streamType
       andStartTime:(int)playbackStartTime
        andFileName:(NSString *)playbackFileName
       andChannelId:(int)channelId
  andIsQuickConnect:(bool)isQuickConnect;

- (void)setVideoPath:(NSString *)path;
//- (NSDictionary *)getPlaybackBarEvents:(int)startTime;
//- (NSDictionary *)getPlaybackAllEvents:(int)startTime;
//- (NSDictionary *)startSpeaker;
//- (NSDictionary *)stopSpeaker;
- (char*)sendNebulaCommand:(NSString*)cmd;
- (void)stopWebRTC;

@property(nonatomic, readonly)  UIView *view;
@property(nonatomic)            NSTimeInterval currentPlaybackTime;
@property(nonatomic, readonly)  NSTimeInterval currentRecordingTime;
@property(nonatomic, readonly)  NSTimeInterval realTime;
@property(nonatomic, readonly)  NSInteger avtechPlaybackStatus;
@property(nonatomic, readonly)  NSTimeInterval duration;
@property(nonatomic, readonly)  NSTimeInterval playableDuration;
@property(nonatomic, readonly)  NSInteger bufferingProgress;

@property(nonatomic, readonly)  BOOL isPreparedToPlay;
@property(nonatomic, readonly)  IJKMPMoviePlaybackState playbackState;
@property(nonatomic, readonly)  IJKMPMovieLoadState loadState;
@property(nonatomic, readonly) int isSeekBuffering;
@property(nonatomic, readonly) int isAudioSync;
@property(nonatomic, readonly) int isVideoSync;

@property(nonatomic, readonly) int64_t numberOfBytesTransferred;

@property(nonatomic, readonly) CGSize naturalSize;
@property(nonatomic) IJKMPMovieScalingMode scalingMode;
@property(nonatomic) BOOL shouldAutoplay;

@property (nonatomic) BOOL allowsMediaAirPlay;
@property (nonatomic) BOOL isDanmakuMediaAirPlay;
@property (nonatomic, readonly) BOOL airPlayMediaActive;

@property (nonatomic) float playbackRate;
@property (nonatomic) float playbackVolume;

@property (nonatomic, readonly) Frame *RGBAFrame;
@property (nonatomic, readonly) AudioFrame *AudioFrame;
@property (nonatomic) int currentX;
@property (nonatomic) int currentY;
@property (nonatomic) NSArray *objTrackList;
@property (nonatomic) NSTimeInterval lastFoundObjectTime;

@property(nonatomic, assign) CGFloat maxScale;
@property(nonatomic, copy) void (^onFling)(CGPoint translation, CGPoint velocity);

- (UIImage *)thumbnailImageAtCurrentTime;

#pragma mark Notifications

#ifdef __cplusplus
#define IJK_EXTERN extern "C" __attribute__((visibility ("default")))
#else
#define IJK_EXTERN extern __attribute__((visibility ("default")))
#endif

// -----------------------------------------------------------------------------
//  MPMediaPlayback.h

// Posted when the prepared state changes of an object conforming to the MPMediaPlayback protocol changes.
// This supersedes MPMoviePlayerContentPreloadDidFinishNotification.
IJK_EXTERN NSString *const IJKMPMediaPlaybackIsPreparedToPlayDidChangeNotification;

// -----------------------------------------------------------------------------
//  MPMoviePlayerController.h
//  Movie Player Notifications

// Posted when the scaling mode changes.
IJK_EXTERN NSString* const IJKMPMoviePlayerScalingModeDidChangeNotification;

// Posted when movie playback ends or a user exits playback.
IJK_EXTERN NSString* const IJKMPMoviePlayerPlaybackDidFinishNotification;
IJK_EXTERN NSString* const IJKMPMoviePlayerPlaybackDidFinishReasonUserInfoKey; // NSNumber (IJKMPMovieFinishReason)

// Posted when the playback state changes, either programatically or by the user.
IJK_EXTERN NSString* const IJKMPMoviePlayerPlaybackStateDidChangeNotification;

// Posted when the network load state changes.
IJK_EXTERN NSString* const IJKMPMoviePlayerLoadStateDidChangeNotification;

// Posted when the movie player begins or ends playing video via AirPlay.
IJK_EXTERN NSString* const IJKMPMoviePlayerIsAirPlayVideoActiveDidChangeNotification;

// -----------------------------------------------------------------------------
// Movie Property Notifications

// Calling -prepareToPlay on the movie player will begin determining movie properties asynchronously.
// These notifications are posted when the associated movie property becomes available.
IJK_EXTERN NSString* const IJKMPMovieNaturalSizeAvailableNotification;

// -----------------------------------------------------------------------------
//  Extend Notifications

IJK_EXTERN NSString *const IJKMPMoviePlayerVideoDecoderOpenNotification;
IJK_EXTERN NSString *const IJKMPMoviePlayerFirstVideoFrameRenderedNotification;
IJK_EXTERN NSString *const IJKMPMoviePlayerFirstAudioFrameRenderedNotification;
IJK_EXTERN NSString *const IJKMPMoviePlayerFirstAudioFrameDecodedNotification;
IJK_EXTERN NSString *const IJKMPMoviePlayerFirstVideoFrameDecodedNotification;
IJK_EXTERN NSString *const IJKMPMoviePlayerOpenInputNotification;
IJK_EXTERN NSString *const IJKMPMoviePlayerFindStreamInfoNotification;
IJK_EXTERN NSString *const IJKMPMoviePlayerComponentOpenNotification;

IJK_EXTERN NSString *const IJKMPMoviePlayerDidSeekCompleteNotification;
IJK_EXTERN NSString *const IJKMPMoviePlayerDidSeekCompleteTargetKey;
IJK_EXTERN NSString *const IJKMPMoviePlayerDidSeekCompleteErrorKey;
IJK_EXTERN NSString *const IJKMPMoviePlayerDidAccurateSeekCompleteCurPos;
IJK_EXTERN NSString *const IJKMPMoviePlayerAccurateSeekCompleteNotification;
IJK_EXTERN NSString *const IJKMPMoviePlayerSeekAudioStartNotification;
IJK_EXTERN NSString *const IJKMPMoviePlayerSeekVideoStartNotification;

IJK_EXTERN NSString *const IJKMPMoviePlayerPlayFrameDroppedNotification;
IJK_EXTERN NSString *const IJKMPMoviePlayerPlayFrameNotDroppedNotification;
IJK_EXTERN NSString *const IJKMPMoviePlayerVideoRecordCompleteNotification;
IJK_EXTERN NSString *const IJKMPMoviePlayerVideoRecordStartNotification;

IJK_EXTERN NSString *const IJKStreamTypeAudioAndVideo;
IJK_EXTERN NSString *const IJKStreamTypeAudioAndSubVideo;
IJK_EXTERN NSString *const IJKStreamTypeVideo;
IJK_EXTERN NSString *const IJKStreamTypeSubVideo;

IJK_EXTERN NSString *const IJKMPMediaShutDownNotification;

@end

#pragma mark IJKMediaUrlOpenDelegate

// Must equal to the defination in ijkavformat/ijkavformat.h
typedef NS_ENUM(NSInteger, IJKMediaEvent) {
    
    // Notify Events
    IJKMediaEvent_WillHttpOpen         = 1,       // attr: url
    IJKMediaEvent_DidHttpOpen          = 2,       // attr: url, error, http_code
    IJKMediaEvent_WillHttpSeek         = 3,       // attr: url, offset
    IJKMediaEvent_DidHttpSeek          = 4,       // attr: url, offset, error, http_code
    // Control Message
    IJKMediaCtrl_WillTcpOpen           = 0x20001, // IJKMediaUrlOpenData: no args
    IJKMediaCtrl_DidTcpOpen            = 0x20002, // IJKMediaUrlOpenData: error, family, ip, port, fd
    IJKMediaCtrl_WillHttpOpen          = 0x20003, // IJKMediaUrlOpenData: url, segmentIndex, retryCounter
    IJKMediaCtrl_WillLiveOpen          = 0x20005, // IJKMediaUrlOpenData: url, retryCounter
    IJKMediaCtrl_WillConcatSegmentOpen = 0x20007, // IJKMediaUrlOpenData: url, segmentIndex, retryCounter
};

#define IJKMediaEventAttrKey_url            @"url"
#define IJKMediaEventAttrKey_host           @"host"
#define IJKMediaEventAttrKey_error          @"error"
#define IJKMediaEventAttrKey_time_of_event  @"time_of_event"
#define IJKMediaEventAttrKey_http_code      @"http_code"
#define IJKMediaEventAttrKey_offset         @"offset"
#define IJKMediaEventAttrKey_file_size      @"file_size"

// event of IJKMediaUrlOpenEvent_xxx
@interface IJKMediaUrlOpenData: NSObject

- (id)initWithUrl:(NSString *)url
            event:(IJKMediaEvent)event
     segmentIndex:(int)segmentIndex
     retryCounter:(int)retryCounter;

@property(nonatomic, readonly) IJKMediaEvent event;
@property(nonatomic, readonly) int segmentIndex;
@property(nonatomic, readonly) int retryCounter;

@property(nonatomic, retain) NSString *url;
@property(nonatomic, assign) int fd;
@property(nonatomic, strong) NSString *msg;
@property(nonatomic) int error; // set a negative value to indicate an error has occured.
@property(nonatomic, getter=isHandled)    BOOL handled;     // auto set to YES if url changed
@property(nonatomic, getter=isUrlChanged) BOOL urlChanged;  // auto set to YES by url changed

@end

@protocol IJKMediaUrlOpenDelegate <NSObject>

- (void)willOpenUrl:(IJKMediaUrlOpenData*) urlOpenData;

@end

@protocol IJKMediaNativeInvokeDelegate <NSObject>

- (int)invoke:(IJKMediaEvent)event attributes:(NSDictionary *)attributes;

@end
