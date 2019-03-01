/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are copyright (C) 2018, Sensia Software LLC
 All Rights Reserved. This software is the property of Sensia Software LLC.
 It cannot be duplicated, used, or distributed without the express written
 consent of Sensia Software LLC.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.datastore.h2;


/**
 * <p>
 * Default ID provider that just increments a local counter
 * </p>
 *
 * @author Alex Robin
 * @date Oct 8, 2018
 */
public class DefaultIdProvider implements IdProvider
{
    long nextId = 1;
    
    
    public DefaultIdProvider(long startFrom)
    {
        this.nextId = startFrom;
    }
    
    
    @Override
    public long newInternalID()
    {
        return nextId++;
    }

}
