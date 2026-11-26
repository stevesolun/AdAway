BEGIN	{
	# we need the number of bytes in a packet to do the output
	# in packet numbers rather than byte numbers.
	if (packetsize <= 0)
		packetsize = 512
	expectNext = 1
	lastwin = -1
	}
$3 != ">" || $1 !~ /^[0-9]+:[0-9]+:[0-9]+([.][0-9]+)?$/ { next }
$6 == "ack" && $7 !~ /^[0-9]+$/ { next }
$6 != "ack" && $5 !~ /[SFR]/ && $6 !~ /^[0-9]+:[0-9]+(\([0-9]+\))?$/ { next }
	{
	# convert tcp trace to send/ack form.
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
		ltim = tim
	}
	if ($6 != "ack") {
		# we have a data packet record:
		# ignore guys with syn, fin or reset 'cause we
		# can't handle their sequence numbers.  Try to
		# detect and add a flag character for 'anomalies':
		#   * -> re-sent packet
		#   - -> packet after hole (missing packet(s))
		#   # -> odd size packet
		if ($5 ~ /[SFR]/)
			next
		split($6, seq, /[:(]/)
		strtSeq = seq[1]+0
		endSeq = seq[2]+0
		len = endSeq - strtSeq
		id = endSeq
		if (id in timeOf)
			flag = "*"
		else {
			timeOf[id] = tim
			if (strtSeq > expectNext)
				flag = "-"
			else if (len != packetsize)
				flag = "#"
			else
				flag = " "
			if (endSeq > expectNext)
				expectNext = endSeq
		}
		printf "%7.2f\t%7.2f\t%s send %s %d", tim-tzero, tim-ltim,\
			flag, $5, strtSeq
		if (++timesSent[id] > 1)
			printf "  (%.2f) [%d]", tim - timeOf[id], timesSent[id]
		if (len != packetsize)
			printf " <%d>", len
	} else {
		id = $7+0

		printf "%7.2f\t%7.2f\t   ack %s %d", tim-tzero, tim-ltim, $5, id
		if ($8 == "win" && $9 ~ /^[0-9]+$/ && $9 != lastwin) {
			printf "  win %d", $9
			lastwin = $9
		}
		# Keep the delay field numeric even when the matching send is missing.
		printf "  (%.2f)", (id in timeOf ? tim - timeOf[id] : -1)
		if (++timesAcked[id] > 1)
			printf " [%d]", timesAcked[id]
	}
	printf "\n"
	ltim = tim
	}
