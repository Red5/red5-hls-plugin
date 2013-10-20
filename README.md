red5-hls-plugin
=======

HLS plugin for Red5

The purpose of this plugin is to provide HLS live streaming using an flash media stream as its source. 

The plugin utilizes Xuggler for transcoding and is provided as-is; any modifications or fixes will be handled as time permits.

Development of this plugin was sponsored by BigMarker (https://www.bigmarker.com/); BTW they're hiring, contact: careers@bigmarker.com

The primary Red5 server project continues to reside on Google Code. Post any issues or wiki entries there.
https://code.google.com/p/red5/

The Red5 users list may be found here: https://groups.google.com/forum/#!forum/red5interest

== Eclipse ==

To create the eclipse project files, execute this within the plugin and / or example directories:
mvn eclipse:eclipse

Then you will be able to import the projects into Eclipse.

== Deployment ==

1. Build both the hls-plugin and the example application (or use your own app). 
2. Place the war in the red5/webapps directory
3. Place the "hls-plugin-1.1.jar" jar in the red5/plugins directory
4. Put the "xuggle-xuggler-5.x.jar" and "xuggle-utils-1.22.jar" jars in the red5/plugins directory
5. Start red5

