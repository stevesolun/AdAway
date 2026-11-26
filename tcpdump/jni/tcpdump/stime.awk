$3 != ">" || $1 !~ /^[0-9]+:[0-9]+:[0-9]+([.][0-9]+)?$/ { next }
$6 == "ack" && $7 !~ /^[0-9]+$/ { next }
$6 != "ack" && $5 !~ /[SFR]/ && $6 !~ /^[0-9]+:[0-9]+(\([0-9]+\))?$/ { next }
	{
	# given a tcpdump ftp trace, output one line for each send
	# in the form
	#   <send time> <seq no>
	# where <send time> is the time packet was sent (in seconds with
	# zero at time of first packet) and <seq no> is the tcp sequence
	# number of the packet divided by 1024 (i.e., Kbytes sent).
	#
	# convert time to seconds
	split($1,t,":")
	clock = t[1]*3600 + t[2]*60 + t[3]
	# Date-less trace: a large backward jump denotes midnight, not a small reorder.
	if (previous - clock > 43200)
		day += 86400
	previous = clock
	tim = day + clock
	if (!started) {
		started = 1
		tzero = tim
	}
	if ($6 == "ack" || $5 ~ /[SFR]/)
		next
	# get packet sequence number
	i = index($6,":")
	printf "%7.2f\t%g\n", tim-tzero, substr($6,1,i-1)/1024
	}
