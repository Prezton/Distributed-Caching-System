#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>

void main() {
    char *path = "./foo";
    int fd = open(path, 0);
    printf("test open %d\n", fd);
    printf("test close %d\n", close(fd));

}