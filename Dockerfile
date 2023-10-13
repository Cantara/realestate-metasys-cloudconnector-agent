FROM ubuntu:20.04
LABEL authors="bard.lind@gmail.com"

ENV USER=rasa
ENV HOME=/home/$USER
ENV APP=rasa

RUN apt-get update && apt-get install -y python3.9 python3.9-dev
RUN apt-get install -y python3-pip
RUN apt-get install vim -y
RUN apt-get install sudo -y

RUN pip3 install rasa

RUN useradd -s /bin/bash -d $HOME -m -G sudo $USER
RUN chown -R $USER:$USER $HOME
RUN echo "alias ll=ls -al" >> $HOME/.bashrc
#RUN adduser -D  -s /bin/sh $USER

#ENTRYPOINT ["top", "-b"]