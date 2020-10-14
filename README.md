# Assigment 1

## Data structure

protocol

```
DATA-SIZE\r\n
DATA-TYPE\r\n
OPERATION PARAMS\r\n
\r\n
DATA
```

sample

```
12
text
MSG 3331

Hello world!
```

response data

```
STATUS\r\n
DATA-SIZE\r\n
DATA-TYPE\r\n
\r\n
DATA\r\n
```

sample

```
200
5
png

BINARY_DATA
```

```
403
16
text

wrong credential
```