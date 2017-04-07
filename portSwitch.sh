port1=$1
port2=$2
if [ x$port1 = "x" -o x$port2 = "x" ];then
echo "命令格式:" $0 "<sport> <dport>"
exit
fi
echo forward port $port1 to $port2
echo '1' > /proc/sys/net/ipv4/ip_forward
iptables -t nat -A PREROUTING -p tcp --dport $port1 -j REDIRECT --to-ports $port2
