#!/bin/sh -e

for template in /templates/*.tpl; do
    file_base="`basename $template | sed s/\.tpl$//`"
    config_file="/etc/nginx/conf.d/$file_base"
    echo "substituting in $template"
    # envsubst < $template | tee $config_file
    cat $template \
        | sed "s,\$HOST_DOMAIN,$HOST_DOMAIN,g" \
        | sed "s,\$SSL_CERT_PATH,$SSL_CERT_PATH,g" \
        | sed "s,\$SSL_KEY_PATH,$SSL_KEY_PATH,g" \
        | tee $config_file

    echo " -> config written to $config_file"
done

echo "executing: nginx"
exec nginx -g "daemon off;"
