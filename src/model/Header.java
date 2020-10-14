package model;

public class Header {
    private char type;
    private int size;
    private int status;

    public Header(char type, int size, int status) {
        this.type = type;
        this.size = size;
        this.status = status;
    }

    public char getType() {
        return type;
    }

    public void setType(char type) {
        this.type = type;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
