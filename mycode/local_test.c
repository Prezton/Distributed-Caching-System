#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>
void main() {
    char *path = "./foo";
    int fd = open(path, O_RDWR);
    char *buf = malloc(sizeof(5));
    char *test = "test2";
    printf("test open %d\n", fd);
    printf("test write %ld\n", write(fd, test, strlen(test)));
    printf("test read %ld\n", read(fd, buf, 5));
    printf("things read %s\n", buf);
    printf("test close %d\n", close(fd));

}