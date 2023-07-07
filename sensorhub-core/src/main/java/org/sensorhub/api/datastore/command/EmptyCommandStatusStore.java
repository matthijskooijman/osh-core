/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2023 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.datastore.command;

import org.sensorhub.api.command.ICommandStatus;
import org.sensorhub.api.common.BigId;
import org.sensorhub.api.datastore.command.ICommandStatusStore.CommandStatusField;
import org.sensorhub.impl.datastore.EmptyDataStore;


/**
 * <p>
 * Helper class to implement databases that don't support all datastores
 * </p>
 * 
 * @author Alex Robin
 * @since Jun 22, 2023
 */
public class EmptyCommandStatusStore
    extends EmptyDataStore<BigId, ICommandStatus, CommandStatusField, CommandStatusFilter> implements ICommandStatusStore
{

    @Override
    public BigId add(ICommandStatus rec)
    {
        throw new UnsupportedOperationException(READ_ONLY_ERROR_MSG);
    }
}
