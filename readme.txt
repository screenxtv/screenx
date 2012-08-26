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
-terminal:
	run the following command
	screen -S screenx
-web:
	copy screenx.html and terminal.js to your webserver(ex:apache) directory


htmlfiles:
	login.html:to login from web(any screenname)
	screenx.html:broadcasting(screenname=screenx)+chat
	sxlogin:login(screenname=screenx)+chat

configuration?
	edit classes/conf_file
	line1:http port(disabled=-1) also edit the code "port=8888" int html/*.html
	line2:https port(disabled=-1)
	line3:width
	line4:height
	line5:keystore file(for httpsserver)
	line6:keystore password
	line7:login password (if pswdlen<=6 then disabled)

security?
	Unless you set the login password, it's probably safe.
	To enable web-login with safety...
	- edit conf_file enable https
	- set a strong password
	- read the code, correct the code(ex: /*&&secure*/ in ScreenX.java, adding an IP-check code) and do it at your own risk
