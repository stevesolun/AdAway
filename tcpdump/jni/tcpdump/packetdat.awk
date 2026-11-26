BEGIN	{
	# we need to know (usual) packet size to convert byte numbers
	# to packet numbers
	if (packetsize <= 0)
		packetsize = 512
	}
$3 != ">" || $1 !~ /^[0-9]+:[0-9]+:[0-9]+([.][0-9]+)?$/ { next }
$6 == "ack" && $7 !~ /^[0-9]+$/ { next }
$6 != "ack" && $6 !~ /^[0-9]+:[0-9]+(\([0-9]+\))?$/ { next }
$5 !~ /[SR]/	{
	# print out per-packet data in the form:
	#  <packet #>
	#  <start sequence #>
	#  <1st send time>
	#  <last send time>
	#  <1st ack time>
	#  <last ack time>
	#  <# sends>
	#  <# acks>

	split($1,t,":")
	clock = t[1]*3600 + t[2]*60 + t[3]
	# Date-less trace: a large backward jump denotes midnight, not a small reorder.
	if (previous - clock > 43200)
		day += 86400
	previous = clock
	tim = day + clock
	if ($6 != "ack") {
		i = index($6,":")
		strtSeq = substr($6,1,i-1)
		id = 1.5 + (strtSeq - 1) / packetsize
		id -= id % 1
		if (maxId < id)
			maxId = id
		if (!totalPackets)
			firstObservedSend = tim
		if (!(id in firstSend)) {
			firstSend[id] = tim
			seqNo[id] = strtSeq
		}
		lastSend[id] = tim
		timesSent[id]++
		totalPackets++
	} else {
		id = 1 + ($7 - 2) / packetsize
		id -= id % 1
		timesAcked[id]++
		if (!(id in firstAck))
			firstAck[id] = tim
		lastAck[id] = tim
		totalAcks++
	}
	}
END	{
	printf "# %d chunks.  %d packets sent.  %d acks.\n", maxId, totalPackets, totalAcks
	# for packets that were implicitly acked, make the ack time
	# be the ack time of next explicitly acked packet.
	# A cumulative ack can cover the last send even if its own send is absent.
	for (id in firstAck)
		if (id+0 > maxId && (!nextId || id+0 < nextId))
			nextId = id+0
	for (i = maxId; i > 0; --i) {
		if (i in firstAck)
			nextId = i
		else if (nextId)
			lastAck[i] = firstAck[i] = firstAck[nextId]
	}
	tzero = (1 in firstSend) ? firstSend[1] : firstObservedSend
	# Keep numeric time columns; -1.00 means no send or covering ack was seen.
	for (i = 1; i <= maxId; i++)
		printf "%d\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%d\t%d\n",\
			i, seqNo[i], \
			(i in firstSend ? firstSend[i] - tzero : -1),\
			(i in lastSend ? lastSend[i] - tzero : -1),\
			(i in firstAck ? firstAck[i] - tzero : -1),\
			(i in lastAck ? lastAck[i] - tzero : -1),\
			timesSent[i], timesAcked[i]
	}
