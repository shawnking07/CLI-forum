# COMP3331/9331 Assignment

## Program design

### the application layer message format

Data from client to server (request) 
similar as HTTP header
```
DATA-SIZE\r\n
DATA-TYPE\r\n
OPERATION PARAMS\r\n
\r\n
DATA
```

Data from server to client (response)


TYPE|DATA_SIZE|STATUS|MSEEAGE DATA
--|--|--|--
char|int|int|binary data
'c'|10|200|"SUCCESS!"

### structure description

The Server use java.NIO to implement non-blocking communication with clients.
Use selector model to manipulate client channel.
And clients use BIO simply implement two threads to separately read and write stream as inputting from terminal will block main process.

## Trade-offs

This implementation does not handle sticky TCP problems,
because our data is not sent continuously and not large.

The application layer protocol of request and response is different.
Because there should be more works for BIO client to split byte array with a delimiter.

For this implementation of server-side, it is not necessary to use file lock, because each command message is handled case by case.
Open and close the file only in need.

## Improvements and extensions

- For Server-side, set a message queue and worker threads to consume these time-consuming tasks 
(we have many commands which need read and write files). When involved in multithreading programming, locks must be considered to use.
- Use Netty framework to simplify NIO programming.
- Standardize the protocol message format for both request and response.