Tomcat CPU Valve
================

A quick Tomcat Valve which allows a limit to be placed on the ammount of CPU time a request can take. If it takes longer than this limit the thread is stoped. This causes a ThreadDeath exception.

Installation
------------

Drop the jar file into the lib folder at the toplevel of the Tomcat distribution.

Configuration
-------------

In your tomcat server configuration file (server.xml) add a Valve element:
  
  <!-- Limit requests to 10 seconds -->
  <Valve class="org.bumph.CPUValve" max="10000"/>
