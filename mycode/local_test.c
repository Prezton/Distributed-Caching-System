#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
void main() {
    char *path = "dir_server_1/../foo";
    int fd = open(path, O_CREAT);
    char *buf = malloc(sizeof(5));
    char *test = "aaaaa";
    printf("test open %d\n", fd);
    printf("test write %ld\n", write(fd, test, strlen(test)));
    // printf("test read %ld\n", read(fd, buf, 5));
    // printf("things read %s\n", buf);
    printf("test close %d\n", close(fd));

}