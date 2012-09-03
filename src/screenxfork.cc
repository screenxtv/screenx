#include <fcntl.h>
#include <stdio.h> 
#include <termios.h> 
#include <sys/types.h> 
#include <sys/ioctl.h>
#include <sys/wait.h> 
#include <signal.h>
#include <sys/types.h>
#include <stdlib.h>
#include <unistd.h>
#include <pthread.h>
#include <errno.h>
#if defined(__GLIBC__)
#include <pty.h>
#elif defined(__FreeBSD__)||defined(__FreeBSD_kernel__)||defined(__DragonFly__)
#include <libutil.h>
#else
#include <util.h>
#endif

int fd,pid;
void chldfunc(int n){wait(NULL);exit(0);}
void* thread_read(void*v){
	char buf[1024];int rd;
	while((rd=read(fd,buf,1024))>0){
		write(1,buf,rd);
	}
	fclose(stdin);
	return NULL;
}
winsize win;
void loop(){
	int c;
	while((c=getc(stdin))>=0){
		char cc=c;
		if(c>0)write(fd,&cc,1);
		else{
			switch(getc(stdin)){
				case -1:return;
				case 0:write(fd,&cc,1);break;
				case 'w':{
						int w=0,h=0;
						for(int i=0;;i++){c=getc(stdin);if(c=='x')break;if(c<'0'||c>'9'||i>3)return;w=w*10+(c-'0');}
						for(int i=0;;i++){c=getc(stdin);if(c==';')break;if(c<'0'||c>'9'||i>3)return;h=h*10+(c-'0');}
						win.ws_col=w;win.ws_row=h;ioctl(fd,TIOCSWINSZ,&win);
					}break;
				default:break;
			}
		}
	}
}
int main(int argc, char *argv[]){
	win.ws_col=atoi(argv[1]);win.ws_row=atoi(argv[2]);
	signal(SIGCHLD,chldfunc);
	if(!(pid=forkpty(&fd,NULL,NULL,&win))){execlp("screen","screen","-x",argc>=4?argv[3]:"screenx","-R",NULL);}
//	if(!(pid=forkpty(&fd,NULL,NULL,&win))){execlp("login","login",NULL);}
	int flag=fcntl(fd,F_GETFL,0);
	fcntl(fd,F_SETFL,O_NONBLOCK);
	for(int i=0;;i++){
		char b;
		int stat=read(fd,&b,1);
		if(stat<0){
			if(errno==EAGAIN&&i<100){usleep(1000*10);continue;}
			else{fclose(stdin);break;}
		}
		write(1,&b,1);
		break;
	}
	fcntl(fd,F_SETFL,flag);
	
	pthread_t ptt;pthread_create(&ptt,NULL,thread_read,NULL);
	loop();
	kill(pid,SIGTERM);
}
