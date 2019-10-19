/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.procedure;

import org.sensorhub.api.datastore.IHistoricalObsDatabase;


/**
 * <p>
 * Interface for databases maintaining the latest state of procedures.
 * </p><p>
 * Although this extends the {@link IHistoricalObsDatabase} interface,
 * implementations are not required to maintain full history, but should rather
 * focus on efficiently maintaining solely the latest state of registered
 * procedures and their outputs.
 * </p>
 *
 * @author Alex Robin
 * @date Oct 19, 2019
 */
public interface IProcedureStateDatabase extends IHistoricalObsDatabase
{

}
