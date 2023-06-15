//
//  NebulaConnect.h
//  IJKMediaPlayer
//
//  Created by Ankit Saini on 02/06/23.
//  Copyright © 2023 bilibili. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "Nebula_interface.h"



@protocol ARDAppClientDelegate;


@interface NebulaConnect : NSObject<ARDAppClientDelegate>

- (instancetype)initWithNebulaApi:(const NebulaAPI *)nebulaAPI;
- (char*)sendNebulaCommand:(NSString*)cmd;

@end
