#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
void main() {
    char *path = "A";
    int fd = open(path, O_RDWR);
    char *buf = malloc(sizeof(1));
    char *test = "q";
    printf("test open %d\n", fd);
    printf("test write %ld\n", write(fd, test, strlen(test)));
    // printf("test read %ld\n", read(fd, buf, 5));
    // printf("things read %s\n", buf);
    printf("test close %d\n", close(fd));

    char *path2 = "F";
    int fd2 = open(path2, O_RDWR);
    char *test2 = "q";
    printf("test write %ld\n", write(fd2, test2, strlen(test2)));
    printf("test close %d\n", close(fd2));

}