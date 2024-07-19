FROM        node:16-bullseye-slim
RUN         curl -fsSL https://deb.nodesource.com/setup_21.x | bash -
RUN         apt-get update

RUN         apt-get install -y openjdk-11-jdk

ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true
ENV PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium
ENV CHROME_PATH=/usr/bin/chromium
ENV DEBIAN_FRONTEND=noninteractive

RUN apt update -qq \
    && apt install -qq -y --no-install-recommends \
      curl \
      git \
      gnupg \
      libgconf-2-4 \
      libxss1 \
      libxtst6 \
      python \
      g++ \
      build-essential \
      chromium \
      chromium-sandbox \
      dumb-init \
      fonts-ipafont-gothic fonts-wqy-zenhei fonts-thai-tlwg fonts-kacst fonts-noto-color-emoji\
    && rm -rf /var/lib/apt/lists/* \
    && rm -rf /src/*.deb
# Copying config files
RUN         npm install shadow-cljs -g
COPY        shadow-cljs.edn /root/shadow-cljs.edn

# Copying source code
WORKDIR     /root 

COPY        resources/backend/package.json       /root/package.json 
COPY        resources/backend/package-lock.json  /root/package-lock.json 
RUN         npm install


COPY        source-code /root/source-code

# Copying resources

COPY        resources   /root/resources
COPY        compile-prod.sh  /root/compile-prod.sh


RUN        ./compile-prod.sh

WORKDIR      /root/resources/backend
CMD         ["node", "core.js"]