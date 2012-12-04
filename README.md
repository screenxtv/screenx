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

   Then, you can see your screen is being broadcasted like the screenshot on the top! Enjoy :-)


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

![ScreenX architecture](http://cdn-ak.f.st-hatena.com/images/fotolife/t/tompng/20120901/20120901160226.png)


## About security

__It's true that ScreenX creates some kind of security holes.__ So, we strongly recommend to run this system on your sandbox-like server first, a server that doesn't have any secret information and no problems whenever down. For example, using [RackHub](http://rackhub.net) and [this 1 line setup gist](https://gist.github.com/3547668), you can easily setup and initialize server with very low cost.

If you use this system on not such servers, we recommend to read the code before running ScreenX in order to understand how dangerous it is. And please do it at your own risk.


## Related Works

- [ScreenX TV](http://screenx.tv) 
- [ScreenX TV Sandbox](https://github.com/yasulab/screenxtv-sandbox)
- [ScreenX TV GCC Client](https://github.com/tompng/screenxtv-gcc-client)
- [ScreenX](https://github.com/tompng/screenx) (内部のソフトウェア)


## Contact

If you find any issue on ScreenX, please let us know it on [this issue tracking system](https://github.com/tompng/screenx/issues).

Also, we're very welcome to your pull requests.


## License

(The MIT License)

Copyright (c) 2012 tomoya ishida

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

