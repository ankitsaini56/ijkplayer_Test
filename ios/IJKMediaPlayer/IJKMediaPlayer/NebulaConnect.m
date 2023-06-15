//
//  NebulaConnect.m
//  IJKMediaPlayer
//
//  Created by Ankit Saini on 02/06/23.
//  Copyright © 2023 bilibili. All rights reserved.
//

#import "NebulaConnect.h"
#import "ARDAppClient.h"

@implementation NebulaConnect {
    ARDAppClient *_client;
}


- (instancetype)initWithNebulaApi:(const NebulaAPI *)nebulaAPI {
    self = [super init];
    if (self) {
        _client = [[ARDAppClient alloc] initWithDelegate:self andNebulaAPI:nebulaAPI];
    }
    return self;
}
- (void)appClient:(ARDAppClient *)client didChangeConnectionState:(RTCIceConnectionState)state {

}

- (void)appClient:(ARDAppClient *)client didChangeState:(ARDAppClientState)state {

}

- (void)appClient:(ARDAppClient *)client didCreateLocalCapturer:(RTCCameraVideoCapturer *)localCapturer {

}

- (void)appClient:(ARDAppClient *)client didError:(NSError *)error {

}

- (void)appClient:(ARDAppClient *)client didGetStats:(NSArray *)stats {

}

- (void)appClient:(ARDAppClient *)client didReceiveLocalVideoTrack:(RTCVideoTrack *)localVideoTrack {

}

- (void)appClient:(ARDAppClient *)client didReceiveRemoteVideoTrack:(RTCVideoTrack *)remoteVideoTrack {

}

- (char*)sendNebulaCommand:(NSString*)cmd {
    return [_client sendCommand:cmd];
}

@end
