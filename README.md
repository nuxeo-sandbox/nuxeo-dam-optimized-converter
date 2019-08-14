## Description
This plugin provides image and video converters optimized for large files:

* the image converter produces all the default conversions with one ImageMagick command. Depending on the file the performance gain goes from 2.5x to 5x.
* the video converter streams the video files directly from S3 instead of making a local copy (requires direct download from S3 to be enabled in nuxeo.conf) 
* very large images (> 1 Gigapixel) are routed in a separate queue because of the very high RAM requirements. These conversions would typically be processed on a dedicated node with a lot more RAM than usual  

[![Build Status](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-dam-optimized-converter-master)](https://qa.nuxeo.org/jenkins/job/Sandbox/job/sandbox_nuxeo-dam-optimized-converter-master/)

## Usage - Configuration

### For default conversions of images: The `MultiOutputPictureResize` converter
The plugin contributes a `converter` (MultiOutputPictureResize) and a `commandline`, and overrides the default listener, so as to use the optimization.

This comes with a price: Automatic conversions declared in the "pictureConversions" extension point are, ultimately, ignored : only the ones declared in the converter/commandline will be applied:

* You can add these conversions to the converter (see below, "Overriding the MultiOutputPictureResize converter"
* You can listen to the `pictureViewsGenerated` event and add them from there.

The way the MultiOutputPictureResize works is the following:

* In the XML converter contribution, it declares the names of the dynamic variables used by the commandline contribution
* It also declares the `-resize` ImageMagic parameter
* It declares prefixes to use for the file name of each generated blob
* It declares the file extension (and so, the conversions) to use for each blob
* And it declares the description oit be used, optionally, in the picture:view field.

(see `picture-converter-contrib.xml` and `commandline-contrib.xml`)

So, if you want to change, for example, the output format for png instead of jpeg, you should just copy the default contribution and paste it in Studio, modifying only the "outputsPrefixes" values. Look at the line `<parameter name="outputsExtensions">.png,.png,.png,.png</parameter>`:

```
<extension point="converter" target="org.nuxeo.ecm.core.convert.service.ConversionServiceImpl">
  <converter class="org.nuxeo.labs.dam.converters.converters.MultiOutputPictureConverter" name="MultiOutputPictureResize">
    <sourceMimeType>image/*</sourceMimeType>
    <sourceMimeType>application/photoshop</sourceMimeType>
    <sourceMimeType>application/illustrator</sourceMimeType>
    <sourceMimeType>application/postscript</sourceMimeType>
    <destinationMimeType>image/jpeg</destinationMimeType>
    <destinationMimeType>image/png</destinationMimeType>
    <parameters>
    <parameter name="CommandLineName">MultiOutputPictureResize</parameter>
    <!--  Name used in the command line as #{the_name} -->
    <parameter name="outputs">FullHDOutputFilePath,MediumOutputFilePath,SmallOutputFilePath,ThumbnailOutputFilePath</parameter>
    <!--  Exact same order as "outputs". Value to pass to the -resize parameter as declared in the command-line -->
    <parameter name="outputsResizesNames">FullHDResize,MediumResize,SmallResize,ThumbnailResize</parameter>
    <!--  Exact same order as "outputs". Value to pass to the -resize parameter -->
    <parameter name="outputsResizes">1920x>,1280x>,640x>,128x></parameter>
    <!--  Exact same order as "outputs" -->
    <parameter name="outputsExtensions">.png,.png,.png,.png</parameter>
    <!--  Exact same order as "outputs" -->
    <!--  Also used as id of each pictures:view. Any trailing "_" is removed -->
    <parameter name="outputsPrefixes">FullHD_,Medium_,Small_,Thumbnail_</parameter>
    <!--  Exact same order as "outputs" -->
    <parameter name="viewsDescriptions">Full HD Size,Medium Size,Small Size,Thumbnail Size</parameter>
    
    </parameters>
  </converter>

</extension>
```

You would do the same if you wanted to change the size of each rendition for example.

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
[Nuxeo](www.nuxeo.com), developer of the leading Content Services Platform, is reinventing enterprise content management (ECM) and digital asset management (DAM). Nuxeo is fundamentally changing how people work with data and content to realize new value from digital information. Its cloud-native platform has been deployed by large enterprises, mid-sized businesses and government agencies worldwide. Customers like Verizon, Electronic Arts, ABN Amro, and the Department of Defense have used Nuxeo's technology to transform the way they do business. Founded in 2008, the company is based in New York with offices across the United States, Europe, and Asia.

Learn more at www.nuxeo.com.
