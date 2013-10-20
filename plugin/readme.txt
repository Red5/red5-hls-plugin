MPEG-TS Plug-in
By: Paul Gregoire

The segmenter service must be configured in your red5-web.xml where the mpeg-ts streaming will be used.

    <bean id="segmenter.service" class="org.red5.service.httpstream.SegmenterService">
        <property name="segmentTimeLimit" value="2000" />
		<!-- Location where segment files will be written -->
        <property name="segmentDirectory" value="webapps/%s/WEB-INF/segments/" />
		<!-- Whether or not to enable memory mapped access, segment files will not be created in this mode -->
        <property name="memoryMapped" value="true" />
        <!-- Time period that the worker sleeps when the queue is empty, in milliseconds -->
        <property name="queueSleepTime" value="500" />
        <!-- Maximum segments to keep in a segment facade -->
        <property name="maxSegmentsPerFacade" value="8" />        
    </bean>
	
The segment directory property may be configured with a full path to where your segments will be written if you are using
file-based segments. If the "%s" is present when this is parsed, it will be replaced with your applications directory.

The web.xml for your application must also have the following servlets defined, which will provide the requested playlist and segments.

    <servlet>
    	<description>Serves an HTTP streaming playlist</description>
    	<display-name>PlayList</display-name>
    	<servlet-name>PlayList</servlet-name>
    	<servlet-class>org.red5.stream.http.servlet.PlayList</servlet-class>
    	<init-param>
            <param-name>minimumSegmentCount</param-name>
            <param-value>3</param-value>
        </init-param>
        <init-param>
            <param-name>startStreamOnRequest</param-name>
            <param-value>true</param-value>
        </init-param>
    </servlet>

    <servlet>
        <description>Serves a segment</description>
        <display-name>TransportSegment</display-name>
    	<servlet-name>TransportSegment</servlet-name>
    	<servlet-class>org.red5.stream.http.servlet.TransportSegment</servlet-class>
    </servlet>
	
    <servlet-mapping>
    	<servlet-name>PlayList</servlet-name>
    	<url-pattern>*.m3u8</url-pattern>
    </servlet-mapping>
    
    <servlet-mapping>
    	<servlet-name>TransportSegment</servlet-name>
    	<url-pattern>*.ts</url-pattern>
    </servlet-mapping>

Lastly, if the playlist and segments are not defined in the primary web.xml for the server they will need to be defined in your
application web.xml as follows.

    <mime-mapping>
        <extension>m3u8</extension>
        <mime-type>application/x-mpegURL</mime-type>
    </mime-mapping>
    
    <mime-mapping>
        <extension>ts</extension>
        <mime-type>video/MP2T</mime-type>
    </mime-mapping>	
	
Special thanks to Infrared5 Inc. for donating the initial code.

Notes:
Cupertino / Apple HLS is very particular with encoding. 
* Minimum of 10 second of chunk (although 2 and 4 seconds have been successfully tested)
* Keyframe interval of 2 (should be no larger than the segment size)
* Use Baseline 3.0

