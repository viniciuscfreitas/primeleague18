#!/bin/bash
if [ -f 'server/plugins/PrimeleagueDiscord/config.yml' ]; then
    sed -i 's/bot-token: ".*"/bot-token: "your-token-here"/' 'server/plugins/PrimeleagueDiscord/config.yml'
fi

