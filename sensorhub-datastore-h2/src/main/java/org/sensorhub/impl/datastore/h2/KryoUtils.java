/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are copyright (C) 2018, Sensia Software LLC
 All Rights Reserved. This software is the property of Sensia Software LLC.
 It cannot be duplicated, used, or distributed without the express written
 consent of Sensia Software LLC.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.datastore.h2;

import java.time.Instant;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Range;


public class KryoUtils
{
    
    public static void writeInstant(Output wbuf, Instant instant)
    {
        wbuf.writeVarLong(instant.getEpochSecond(), true);
        wbuf.writeInt(instant.getNano());
    }
    
    
    public static Instant readInstant(Input buf)
    {
        long epochSeconds = buf.readVarLong(true);
        int nanos = buf.readInt();
        return Instant.ofEpochSecond(epochSeconds, nanos);
    }
    
    
    public static void writeTimeRange(Output wbuf, Range<Instant> timeRange)
    {
        // flags (determines presence of range and its bounds)
        byte flags = 0;
        if (timeRange == null)
        {
            wbuf.writeByte(flags);
            return;
        }
        
        if (timeRange.hasLowerBound())
            flags += 1;
        if (timeRange.hasUpperBound())
            flags += 2;
        wbuf.writeByte(flags);
        
        // lower bound
        if (timeRange.hasLowerBound())
            writeInstant(wbuf, timeRange.lowerEndpoint());
        
        // upper bound
        if (timeRange.hasUpperBound())
            writeInstant(wbuf, timeRange.upperEndpoint());
    }
    
    
    public static Range<Instant> readTimeRange(Input buf)
    {
        // read flags
        byte flags = buf.readByte();
        if (flags == 0)
            return null;
        
        // read bound lower and upper bounds
        Instant lower =  ((flags & 1) != 0) ? readInstant(buf) : null;
        Instant upper =  ((flags & 2) != 0) ? readInstant(buf) : null;
        
        if (upper == null)
            return Range.atLeast(lower);
        else if (lower == null)
            return Range.atMost(upper);
        else
            return Range.closed(lower, upper);
    }

    
}
