what's screenx?
	terminal broadcasting system + chat
	remote login from web
	
	this program uses "screen" command.
	the screen[name="screenx"] will be broadcasted.

how to use?
-build:
	./build.sh
-run server:
	cd classes
	java ScreenX
	//nohup java ScreenX &
-terminal:
	run the following command
	screen -S screenx
-web:
	open http://[hostname]:[port]/screenx.html
	or you can copy the folder 'http' to your webserver directory.

htmlfiles
	login.html:to login from web(any screenname)
	screenx.html:broadcasting(screenname=screenx)+chat
	sxlogin:login(screenname=screenx)+chat

configuration?
	edit classes/screenx.conf and restart screenx server.
	HttpPort,HttpsPort: you should also edit http/sxconfig.js
	EnableLogin: login from web(only https)
	EnableHttpLogin: login from http
	LoginPassword: change this before you enable login. (algorithm:challangecode)


security?
	Read the code.
	If there is some problem, correct the code.
	Do it at your own risk.
