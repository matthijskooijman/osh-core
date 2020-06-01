/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.

Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.

******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.common;


/**
 * <p>
 * Immutable data class containing both internal ID and globally unique ID
 * (URI) of a procedure.
 * </p>
 *
 * @author Alex Robin
 * @date May 1, 2020
 */
public class ProcedureId extends FeatureId
{

    public ProcedureId(long internalID, String uid)
    {
        super(internalID, uid);
    }
}
