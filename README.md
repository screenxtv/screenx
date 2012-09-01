# What's screenx?

### ScreenX is a real-time terminal broadcasting system. It enables you to broadcast your screen and control it from web browser.

![Screenshot of ScreenX](https://dl.dropbox.com/u/2819285/screenx-ss.png)

## Requirements
- `screen` command (`sudo aptitude install screen` for Unix-like system user)
  
  A screen named "screenx" will be broadcasted. (ex. `screen -x screenx`).

- `java` and `javac` commands to build and run

- `g++` command to build



## How to use

1. download source code of ScreenX:
   
   `git clone https://github.com/tompng/screenx.git`.

2. build ScreenX:

   `cd screenx`
   
   `./build.sh` (make sure you can use `javac` and `g++`)
   
3. run Java server:

   `cd classes`
   
   `java ScreenX` (make sure you can use `java`)

       NOTE: if you'd like to run ScreenX in background, use `nohup java ScreenX &` instead.
      
4. start broadcasting your screen:

   `screen -x screenx`

5. create symbolic links of `html/*` to, for example, `/var/www/`:

   e.g. `sudo ln -s html/* /var/www/` on Zsh

6. open `http://[hostname]:[port]/screenx.html` with your web browser.



## Settings

- `html/`:

     - `login.html`:   to login from web (any screenname)
     - `sxlogin.html`: to login (default screenname is `screenx`) + chat     
     - `screenx.html`: to watch the broadcasted screen named `screenx` + chat


- For configuration, edit `classes/screenx.conf`: and restart screenx server.
     - `HttpPort` / `HttpsPort`: you should also edit `http/sxconfig.js`
     - `EnableLogin`: login from web (only https)
     - `EnableHttpLogin`: login from http
     - `LoginPassword`: change this before you enable login. (algorithm: challangecode)

## Use case

Maybe you can use this sytem for the following scenes:

1. Coding Interview
2. Remote Pair-programming
3. Real-time Hackathon

Of course you can use ScreenX in any other situations not listed above :-)

## System Architecture

![ScreenX architecture](http://hensatibu.moe.hm/~tomoya/architecture.png)


## About security

__It's true that ScreenX creates some kind of security holes.__ So, we strongly recommend to run this system on your sandbox-like server first, a server that doesn't have any secret information and no problems whenever down. For example, using [RackHub](http://rackhub.net) and [this 1 line setup gist](https://gist.github.com/3547668), you can easily setup and initialize server with very low cost.

If you use this system on not such servers, we recommend to read the code before running ScreenX in order to understand how dangerous it is. And please do it at your own risk.

## Contact

If you find any problem on ScreenX, we're very welcome to correct the code. Please send us your pull requests.

## License

ScreenX is distributed under MIT License. See `LICENSE` for details.
