CC = gcc
CFLAGS = -std=c11 -O2 -Wall -Wextra -Werror -fPIC
LDFLAGS = -shared
LIBS = -lm

TARGET_LIB = libfinanzas.so
TARGET_TEST = test_finanzas

.PHONY: all test clean

all: $(TARGET_LIB)

$(TARGET_LIB): finanzas.c finanzas.h
	$(CC) $(CFLAGS) $(LDFLAGS) -o $(TARGET_LIB) finanzas.c $(LIBS)

$(TARGET_TEST): test_finanzas.c $(TARGET_LIB)
	$(CC) -std=c11 -O2 -Wall -Wextra -Werror -o $(TARGET_TEST) test_finanzas.c -L. -lfinanzas $(LIBS)

test: $(TARGET_TEST)
	LD_LIBRARY_PATH=. ./$(TARGET_TEST)

clean:
	rm -f $(TARGET_LIB) $(TARGET_TEST)
