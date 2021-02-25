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

@synthesize pixels = _pixels;
@synthesize width = _width;
@synthesize height = _height;
@synthesize roi = _roi;

- (instancetype)initFrame:(NSData *)pixels
                withWidth:(int)w
                andHeight:(int)h
                   andROI:(CGRect)roi;
{
    self = [super init];
    _pixels = pixels;
    _width = w;
    _height = h;
    _roi = roi;
    return self;
}
@end
