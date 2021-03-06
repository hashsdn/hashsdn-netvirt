/*
 * Copyright (c) 2016, 2017 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.translator.portchain;

import java.util.List;
import java.util.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.sfc.translator.DelegatingDataTreeListener;
import org.opendaylight.netvirt.sfc.translator.NeutronMdsalHelper;
import org.opendaylight.netvirt.sfc.translator.OvsdbMdsalHelper;
import org.opendaylight.netvirt.sfc.translator.SfcMdsalHelper;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.function.base.SfDataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunctionKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarderBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarderKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.service.function.forwarder.ServiceFunctionDictionary;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.PortPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pairs.PortPair;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenDaylight Neutron Port Pair yang models data change listener.
 */
public class NeutronPortPairListener extends DelegatingDataTreeListener<PortPair> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronPortPairListener.class);

    private static final InstanceIdentifier<PortPair> PORT_PAIR_IID =
            InstanceIdentifier.create(Neutron.class).child(PortPairs.class).child(PortPair.class);

    private final DataBroker db;
    private final SfcMdsalHelper sfcMdsalHelper;
    private final NeutronMdsalHelper neutronMdsalHelper;
    private final OvsdbMdsalHelper ovsdbMdsalHelper;

    public NeutronPortPairListener(DataBroker db) {
        super(db,new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, PORT_PAIR_IID));
        this.db = db;
        sfcMdsalHelper = new SfcMdsalHelper(db);
        neutronMdsalHelper = new NeutronMdsalHelper(db);
        ovsdbMdsalHelper = new OvsdbMdsalHelper(db);
    }

    /**
     * Method removes PortPair which is identified by InstanceIdentifier.
     *
     * @param path - the whole path to PortPair
     * @param deletedPortPair        - PortPair for removing
     */
    @Override
    public void remove(InstanceIdentifier<PortPair> path, PortPair deletedPortPair) {

        ServiceFunctionKey sfKey = PortPairTranslator.getSFKey(deletedPortPair);
        ServiceFunction serviceFunction = sfcMdsalHelper.readServiceFunction(sfKey);
        if (serviceFunction == null) {
            LOG.info("Service Function related to Port Pair {} don't exist.", deletedPortPair);
            return;
        }

        List<SfDataPlaneLocator> sfDataPlaneLocatorList = serviceFunction.getSfDataPlaneLocator();
        if (sfDataPlaneLocatorList == null) {
            return;
        }

        for (SfDataPlaneLocator sfDpl : sfDataPlaneLocatorList) {

            ServiceFunctionForwarder sff = Optional.ofNullable(sfDpl.getServiceFunctionForwarder())
                    .map(ServiceFunctionForwarderKey::new)
                    .map(sfcMdsalHelper::readServiceFunctionForwarder)
                    .orElse(null);
            if (sff == null) {
                continue;
            }

            List<ServiceFunctionDictionary> serviceFunctionDictionaryList = sff.getServiceFunctionDictionary();
            if (serviceFunctionDictionaryList == null) {
                continue;
            }

            boolean isSfDictionaryRemoved = serviceFunctionDictionaryList.stream()
                    .filter(serviceFunctionDictionary -> sfKey.getName().equals(serviceFunctionDictionary.getName()))
                    .findFirst()
                    .map(serviceFunctionDictionaryList::remove)
                    .orElse(false);

            if (isSfDictionaryRemoved && serviceFunctionDictionaryList.isEmpty()) {
                //If the current SF is last SF attach to SFF, delete the SFF
                sfcMdsalHelper.deleteServiceFunctionForwarder(sff.getKey());
            } else if (isSfDictionaryRemoved) {
                //If there are SF attached to the SFF, just update the SFF dictionary by removing
                // this deleted SF.
                ServiceFunctionForwarderBuilder sffBuilder = new ServiceFunctionForwarderBuilder(sff);
                LOG.info("Remove the SF {} from SFF {} dictionary and update in data store.",
                        sfKey.getName(),
                        sfDpl.getServiceFunctionForwarder());
                sfcMdsalHelper.addServiceFunctionForwarder(sffBuilder.build());
            }
        }

        sfcMdsalHelper.removeServiceFunction(sfKey);
    }

    /**
     * Method updates the original PortPair to the update PortPair.
     * Both are identified by same InstanceIdentifier.
     *
     * @param path - the whole path to PortPair
     * @param originalPortPair   - original PortPair (for update)
     * @param updatePortPair     - changed PortPair (contain updates)
     */
    @Override
    public void update(InstanceIdentifier<PortPair> path, PortPair originalPortPair, PortPair updatePortPair) {
        //NO-OP
    }

    /**
     * Method adds the PortPair which is identified by InstanceIdentifier
     * to device.
     *
     * @param path - the whole path to new PortPair
     * @param newPortPair        - new PortPair
     */
    @Override
    public void add(InstanceIdentifier<PortPair> path, PortPair newPortPair) {
        //NO-OP
        // Port Pair data written in neutron data store will be used
        // When user will create port chain.
    }
}
