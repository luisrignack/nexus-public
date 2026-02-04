<!--

    Sonatype Nexus (TM) Open Source Version
    Copyright (c) 2008-present Sonatype, Inc.
    All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.

    This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
    which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.

    Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
    of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
    Eclipse Foundation. All other trademarks are the property of their respective owners.

-->
# jetty-modifications

This set of modules contains modifications to [Eclipse Jetty](https://jetty.org/) in order to fully support features 
available in Nexus Repository when configured to use Sonatype Repository Firewall.

The modifications are based on the Jetty 12.0.17 release. The maven modules within this folder download the 
original Jetty source code, apply the modifications, and rebuild the modified Jetty artifacts.

Eclipse Jetty is licensed under the Apache License, Version 2.0, and the Eclipse Public License, Version 2.0. 

https://github.com/jetty/jetty.project?tab=License-1-ov-file#readme

As permitted by the original copyright holders, Sonatype elects to distribute modifications to these Jetty modules
under the Apache License, Version 2.0, exclusively.

