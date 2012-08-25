what's screenx?
	terminal broadcasting system + chat
	remote login from web

how to use?
build:
	./build.sh
run:
	cd classes
	java ScreenX

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
	- read the code, correct the code(ex: /*&&secure*/ at ScreenX.java, add an IP-check code) and do it at your own risk

