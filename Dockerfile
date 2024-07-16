
#####################################
#  Node dependencies backend cache  #
#####################################


FROM        node:lts-slim as node-dependencies-backend
COPY        resources/backend/package.json       /root/package.json 
COPY        resources/backend/package-lock.json  /root/package-lock.json 
WORKDIR     /root 
RUN         npm install



#########################
#  Clojure compilation  #
#########################

FROM        clojure:temurin-21-tools-deps-jammy as wizard-compiler
RUN         curl -fsSL https://deb.nodesource.com/setup_21.x | bash -
RUN         apt-get update
RUN         apt-get install -y nodejs
# Copying config files
RUN         npm install shadow-cljs -g
RUN         npm install webpack -g
RUN         npm install webpack-cli -g
COPY        shadow-cljs.edn /root/shadow-cljs.edn

# Copying source code

COPY        source-code /root/source-code

# Copying resources

COPY        resources   /root/resources
COPY        compile-prod.sh  /root/compile-prod.sh

WORKDIR     /root
COPY        --from=node-dependencies-backend   /root/node_modules  /root/resources/backend/node_modules

RUN        ./compile-prod.sh

WORKDIR      /root/resources/backend
CMD         ["node", "core.js"]