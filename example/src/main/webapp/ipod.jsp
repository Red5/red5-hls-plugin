<%@page %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
     "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8" />
<title>HLS</title>
</head>
<body>
<form>
Enter a stream name to view: 
<input type="text" id="streamName" name="streamName" />
<input type="submit" />
</form>
<%
String streamName = request.getParameter("streamName");
if (streamName == null) {
	streamName = "mondain";
}
%>
<ul>
<li><a href="<%= streamName %>.m3u8">M3U8</a></li>
<li><a href="hlsapp-audio.m3u8">Muxed audio</a> (audio only)</li>
<li><a href="mystream.m3u8">MyStream</a> (audio and video)</li>
<li><a href="mystreamtwo.m3u8">MyStreamTwo</a> (audio and video)</li>
</ul>
</body>
</html>
