package no.messom.chatbot;

public class SmsMessage {

    private String msg;
    private String number;

    public SmsMessage() {
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    @Override
    public String toString() {
        return "SmsMessage{" +
                "msg='" + msg + '\'' +
                ", number='" + number + '\'' +
                '}';
    }
}
