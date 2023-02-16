/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#import <Foundation/Foundation.h>
#import <WebRTC/RTCAudioTrack.h>
#import <WebRTC/RTCPeerConnection.h>
#import <WebRTC/RTCVideoTrack.h>
#import "Nebula_interface.h"

typedef NS_ENUM(NSInteger, ARDAppClientState) {
  // Disconnected from servers.
  kARDAppClientStateDisconnected,
  // Connecting to servers.
  kARDAppClientStateConnecting,
  // Connected to servers.
  kARDAppClientStateConnected,
};

@class ARDAppClient;
@class ARDSettingsModel;
@class ARDExternalSampleCapturer;
@class RTC_OBJC_TYPE(RTCMediaConstraints);
@class RTC_OBJC_TYPE(RTCCameraVideoCapturer);
@class RTC_OBJC_TYPE(RTCFileVideoCapturer);

// The delegate is informed of pertinent events and will be called on the
// main queue.
@protocol ARDAppClientDelegate <NSObject>

- (void)appClient:(ARDAppClient *)client didChangeState:(ARDAppClientState)state;

- (void)appClient:(ARDAppClient *)client didChangeConnectionState:(RTCIceConnectionState)state;

- (void)appClient:(ARDAppClient *)client
    didCreateLocalCapturer:(RTC_OBJC_TYPE(RTCCameraVideoCapturer) *)localCapturer;

- (void)appClient:(ARDAppClient *)client
    didReceiveLocalVideoTrack:(RTC_OBJC_TYPE(RTCVideoTrack) *)localVideoTrack;

- (void)appClient:(ARDAppClient *)client
    didReceiveRemoteVideoTrack:(RTC_OBJC_TYPE(RTCVideoTrack) *)remoteVideoTrack;

- (void)appClient:(ARDAppClient *)client didError:(NSError *)error;

- (void)appClient:(ARDAppClient *)client didGetStats:(NSArray *)stats;

@optional
- (void)appClient:(ARDAppClient *)client
    didCreateLocalFileCapturer:(RTC_OBJC_TYPE(RTCFileVideoCapturer) *)fileCapturer;

- (void)appClient:(ARDAppClient *)client
    didCreateLocalExternalSampleCapturer:(ARDExternalSampleCapturer *)externalSampleCapturer;

@end

// Handles connections to the AppRTC server for a given room. Methods on this
// class should only be called from the main queue.
@interface ARDAppClient : NSObject

// If |shouldGetStats| is true, stats will be reported in 1s intervals through
// the delegate.
@property(nonatomic, assign) BOOL shouldGetStats;
@property(nonatomic, readonly) ARDAppClientState state;
@property(nonatomic, weak) id<ARDAppClientDelegate> delegate;
@property(nonatomic, assign, getter=isBroadcast) BOOL broadcast;
@property(nonatomic, readwrite) RTCIceGatheringState iceGatherState;
@property(nonatomic, readwrite) NSMutableArray *iceCandidates;
@property(nonatomic, readonly) RTC_OBJC_TYPE(RTCAudioTrack) *micAudioTrack;
// Convenience constructor since all expected use cases will need a delegate
// in order to receive remote tracks.
- (instancetype)initWithDelegate:(id<ARDAppClientDelegate>)delegate
                    andNebulaAPI:(const NebulaAPI *)nebulaAPI;

// Establishes a connection with the AppRTC servers for the given room id.
// |settings| is an object containing settings such as video codec for the call.
// If |isLoopback| is true, the call will connect to itself.
- (long)connectToRoomWithId:(NSString *)roomId
                   settings:(ARDSettingsModel *)settings
                 isLoopback:(BOOL)isLoopback;

// Disconnects from the AppRTC servers and any connected clients.
- (void)disconnect;
- (long)getWebRTCApi;
- (NSMutableArray*)buildIceServer;
- (NSMutableArray*)buildIceServer:(NSDictionary *)json;
- (char *)sendCommand:(NSString *)cmd;
- (NSString *)genStartLiveStreamEx:(int)channelId
                        streamType:(NSString *)streamType;
- (NSString *)genStartWebRtc:(NSString *)dmToken
                       realm:(NSString *)realm
                        info:(NSDictionary *)info
                   channelId:(int)channelId
                    streamId:(NSString *)streamId;
- (NSString *)genStartWebRtcEx:(NSString *)dmToken
                       realm:(NSString *)realm
                        info:(NSDictionary *)info
                   channelId:(int)channelId
                    streamType:(NSString *)streamType;
- (NSString *)genStopWebRtc;
- (NSString *)genExchangeSdp:(NSString *)sdp
                         type:(NSString *)type;
- (NSString *)genStartWebRtcStreams;
- (NSString *)genStopWebRtcStreams;
- (NSString *)combineOfferWithIceCandidate:(NSString *)sdp;
- (void)separateIceCandidateWithAnswer:(NSString *)sdp;
- (void)setMicEnable:(BOOL)enable;
- (NSDictionary *)getPlaybackBarEvents:(int)startTime;
- (NSDictionary *)getPlaybackAllEvents:(int)startTime;
@end
