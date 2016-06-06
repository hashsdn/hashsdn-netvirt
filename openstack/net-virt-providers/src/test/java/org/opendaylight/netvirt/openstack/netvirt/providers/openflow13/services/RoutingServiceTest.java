/*
 * Copyright (c) 2015 Inocybe and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.services;

import com.google.common.util.concurrent.CheckedFuture;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netvirt.openstack.netvirt.api.Action;
import org.opendaylight.netvirt.openstack.netvirt.api.Status;
import org.opendaylight.netvirt.openstack.netvirt.api.StatusCode;
import org.opendaylight.netvirt.openstack.netvirt.providers.NetvirtProvidersProvider;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.PipelineOrchestrator;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.Service;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.powermock.api.support.membermodification.MemberModifier;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Unit test fort {@link RoutingService}
 */
@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("unchecked")
public class RoutingServiceTest {

    @InjectMocks private RoutingService routingService = new RoutingService();

    @Mock private DataBroker dataBroker;
    @Mock private PipelineOrchestrator orchestrator;

    @Mock private WriteTransaction writeTransaction;
    @Mock private CheckedFuture<Void, TransactionCommitFailedException> commitFuture;

    private static final String SEGMENTATION_ID = "2";
    private static final String HOST_ADDRESS = "127.0.0.1";
    private static final String IPV6_HOST_ADDRESS = "2001:db8::1";
    private static final String MAC_ADDRESS = "87:1D:5E:02:40:B8";

    @Before
    public void setUp() throws Exception {
        when(writeTransaction.submit()).thenReturn(commitFuture);

        when(dataBroker.newWriteOnlyTransaction()).thenReturn(writeTransaction);

        when(orchestrator.getNextServiceInPipeline(any(Service.class))).thenReturn(Service.ARP_RESPONDER);
        NetvirtProvidersProvider netvirtProvider = mock(NetvirtProvidersProvider.class);
        MemberModifier.field(NetvirtProvidersProvider.class, "hasProviderEntityOwnership").set(netvirtProvider, new AtomicBoolean(true));
    }

    /**
     * Test method {@link RoutingService#programRouterInterface(Long, String, String, String, InetAddress, int, Action)}
     */
    @Test
    public void testProgramRouterInterface() throws Exception {
        InetAddress address = mock(InetAddress.class);
        when(address.getHostAddress()).thenReturn(HOST_ADDRESS);

        assertEquals("Error, did not return the expected StatusCode",
                new Status(StatusCode.SUCCESS),
                routingService.programRouterInterface(Long.valueOf(123),
                        SEGMENTATION_ID, SEGMENTATION_ID, MAC_ADDRESS, address, 1, Action.ADD));
        verify(writeTransaction, times(2)).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Node.class), anyBoolean());
        verify(writeTransaction, times(1)).submit();
        verify(commitFuture, times(1)).get();

        assertEquals("Error, did not return the expected StatusCode",
                new Status(StatusCode.SUCCESS),
                routingService.programRouterInterface(Long.valueOf(123),
                        SEGMENTATION_ID, SEGMENTATION_ID, MAC_ADDRESS, address, 1, Action.DELETE));
        verify(writeTransaction, times(1)).delete(any(LogicalDatastoreType.class), any(InstanceIdentifier.class));
        verify(writeTransaction, times(2)).submit();
        verify(commitFuture, times(2)).get(); // 1 + 1 above
    }

    /**
     * Test method {@link RoutingService#programRouterInterface(Long, String, String, String, InetAddress, int, Action)}
     */
    @Test
    public void testProgramRouterInterfaceForIpv6() throws Exception {
        InetAddress address = InetAddress.getByName(IPV6_HOST_ADDRESS);

        assertEquals("Error, did not return the expected StatusCode",
                new Status(StatusCode.SUCCESS),
                routingService.programRouterInterface(Long.valueOf(123),
                        SEGMENTATION_ID, SEGMENTATION_ID, MAC_ADDRESS, address, 64, Action.ADD));
        verify(writeTransaction, times(1)).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Node.class), anyBoolean());
        verify(writeTransaction, times(1)).submit();
        verify(commitFuture, times(1)).checkedGet();

        assertEquals("Error, did not return the expected StatusCode",
                new Status(StatusCode.SUCCESS),
                routingService.programRouterInterface(Long.valueOf(123),
                        SEGMENTATION_ID, SEGMENTATION_ID, MAC_ADDRESS, address, 1, Action.DELETE));
        verify(writeTransaction, times(1)).delete(any(LogicalDatastoreType.class), any(InstanceIdentifier.class));
        verify(writeTransaction, times(2)).submit();
        verify(commitFuture, times(1)).get();
    }

    /**
     * Test method {@link RoutingService#programDefaultRouteEntry(Long, String, String, InetAddress, Action)}
     */
    @Test
    public void testProgramDefaultRouteEntry() throws Exception {
        InetAddress address = mock(InetAddress.class);
        when(address.getHostAddress()).thenReturn(HOST_ADDRESS);

        assertEquals("Error, did not return the expected StatusCode",
                new Status(StatusCode.SUCCESS),
                routingService.programDefaultRouteEntry(Long.valueOf(123),
                        SEGMENTATION_ID, MAC_ADDRESS, address, Action.ADD));
        verify(writeTransaction, times(2)).put(any(LogicalDatastoreType.class),
                any(InstanceIdentifier.class), any(Node.class), anyBoolean());
        verify(writeTransaction, times(1)).submit();
        verify(commitFuture, times(1)).get();

        assertEquals("Error, did not return the expected StatusCode",
                new Status(StatusCode.SUCCESS),
                routingService.programDefaultRouteEntry(Long.valueOf(123),
                        SEGMENTATION_ID, MAC_ADDRESS, address, Action.DELETE));
        verify(writeTransaction, times(1)).delete(any(LogicalDatastoreType.class), any(InstanceIdentifier.class));
        verify(writeTransaction, times(2)).submit();
        verify(commitFuture, times(2)).get(); // 1 + 1 above
    }
}
