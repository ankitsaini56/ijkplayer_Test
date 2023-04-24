//
//  IJKFrame.m
//  IJKMediaFramework
//
//  Created by Nith on 2019/8/30.
//  Copyright © 2019 bilibili. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <IJKMediaPlayback.h>

@implementation Frame

- (instancetype)initFrame:(NSData *)pixels
                withWidth:(int)w
                andHeight:(int)h
          andObjTrackList:(NSArray *)objTrackList;
{
    self = [super init];
    _pixels = pixels;
    _width = w;
    _height = h;
    _objTrackList = objTrackList;
    return self;
}
@end

@implementation AudioFrame

- (instancetype)initFrame:(NSData *)data
                withSampleRate:(int)sampleRate
                withChannels:(int)channels
          andBitsPerSample:(int)bitsPerSample;
{
    self = [super init];
    _data = data;
    _sampleRate = sampleRate;
    _channels = channels;
    _bitsPerSample = bitsPerSample;
    return self;
}
@end

@implementation ObjectTrackingInfo

- (instancetype)initObjectTrackingInfo:(CGRect)rect
                         withCategory:(NSString *)category
                            andVector:(NSArray *)vector;
{
    self = [super init];
    _rect = rect;
    _category = category;
    _vector = vector;
    return self;
}
@end
