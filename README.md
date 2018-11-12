## Description
This plugin provides image and video converters optimized for large files:
- the image converter produces all the default conversions with one ImageMagick command. Depending on the file the performance gain goes from 2.5x to 5x.
- the video converter streams the video files directly from S3 instead of making a local copy (requires direct download from S3 to be enabled in nuxeo.conf) 
- very large images (> 1 Gigapixel) are routed in a seperate queue because of the very high RAM requirements. These conversions would typically be processed on a dedicated node with a lot more RAM than usual  

[![Build Status](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-dam-optimized-converter-master)](https://qa.nuxeo.org/jenkins/job/Sandbox/job/sandbox_nuxeo-dam-optimized-converter-master/)

## Important Note

**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be useful for the Nuxeo Platform in general, they will be integrated directly into platform, not maintained here.

## Requirements
Building requires the following software:
- git
- maven
- ffmpeg
- imagemagick

## How to build
```
git clone https://github.com/nuxeo-sandbox/nuxeo-dam-optimized-converter
cd nuxeo-dam-optimized-converter
mvn clean install
```

## Deploying
- Install the marketplace package from the admin center or using nuxeoctl

## Configuration
For 

## Known limitations
This plugin is a work in progress.

## About Nuxeo
Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris. More information is available at [www.nuxeo.com](http://www.nuxeo.com).
