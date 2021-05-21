/*
 * Copyright 2016 Daniel Vimont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonvox.hbase_column_manager;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Running of these methods requires that an up-and-running instance of HBase be accessible. (The
 * emulation environment provided by HBaseTestUtility is not appropriate for these tests.)
 *
 * @author Daniel Vimont
 */
public class TestRepositoryAdmin {

  private static final List<String> TEST_NAMESPACE_LIST
          = new ArrayList<>(Arrays.asList("testNamespace01", "testNamespace02", "testNamespace03"));
  private static final List<String> TEST_TABLE_NAME_LIST
          = new ArrayList<>(
                  Arrays.asList("testTable01", "testTable02", "testTable03", "testTable04"));
  private static final List<byte[]> TEST_COLUMN_FAMILY_LIST
          = new ArrayList<>(Arrays.asList(Bytes.toBytes("CF1"), Bytes.toBytes("CF2")));
  private static final List<byte[]> TEST_COLUMN_QUALIFIER_LIST
          = new ArrayList<>(Arrays.asList(Bytes.toBytes("column01"), Bytes.toBytes("column02"),
                          Bytes.toBytes("column03"), Bytes.toBytes("column04")));
  private static final byte[] QUALIFIER_IN_EXCLUDED_TABLE = Bytes.toBytes("qualifierOnExcludedTable");
  private static final byte[] ROW_ID_01 = Bytes.toBytes("rowId01");
  private static final byte[] ROW_ID_02 = Bytes.toBytes("rowId02");
  private static final byte[] ROW_ID_03 = Bytes.toBytes("rowId03");
  private static final byte[] ROW_ID_04 = Bytes.toBytes("rowId04");
  private static final byte[] VALUE_2_BYTES_LONG = Bytes.toBytes("xy");
  private static final byte[] VALUE_5_BYTES_LONG = Bytes.toBytes("54321");
  private static final byte[] VALUE_9_BYTES_LONG = Bytes.toBytes("123456789");
  private static final byte[] VALUE_82_BYTES_LONG = new byte[82];
  static {
    Arrays.fill(VALUE_82_BYTES_LONG, (byte) 'A');
  }
  private static final int NAMESPACE01_INDEX = 0;
  // namespace02 is NOT included in audit processing
  private static final int NAMESPACE02_INDEX = 1;
  // namespace03's table02 & table04 NOT included in audit processing
  private static final int NAMESPACE03_INDEX = 2;
  private static final int TABLE01_INDEX = 0;
  private static final int TABLE02_INDEX = 1;
  private static final int TABLE03_INDEX = 2;
  private static final int TABLE04_INDEX = 3;
  private static final int CF01_INDEX = 0;
  private static final int CF02_INDEX = 1;

  private static final TableName NAMESPACE01_TABLE01
          = TableName.valueOf(TEST_NAMESPACE_LIST.get(NAMESPACE01_INDEX),
                  TEST_TABLE_NAME_LIST.get(TABLE01_INDEX));
  private static final TableName NAMESPACE01_TABLE02
          = TableName.valueOf(TEST_NAMESPACE_LIST.get(NAMESPACE01_INDEX),
                  TEST_TABLE_NAME_LIST.get(TABLE02_INDEX));
  private static final TableName NAMESPACE01_TABLE03
          = TableName.valueOf(TEST_NAMESPACE_LIST.get(NAMESPACE01_INDEX),
                  TEST_TABLE_NAME_LIST.get(TABLE03_INDEX));
  private static final TableName NAMESPACE01_TABLE04
          = TableName.valueOf(TEST_NAMESPACE_LIST.get(NAMESPACE01_INDEX),
                  TEST_TABLE_NAME_LIST.get(TABLE04_INDEX));
  private static final TableName NAMESPACE02_TABLE01
          = TableName.valueOf(TEST_NAMESPACE_LIST.get(NAMESPACE02_INDEX),
                  TEST_TABLE_NAME_LIST.get(TABLE01_INDEX));
  private static final TableName NAMESPACE02_TABLE02
          = TableName.valueOf(TEST_NAMESPACE_LIST.get(NAMESPACE02_INDEX),
                  TEST_TABLE_NAME_LIST.get(TABLE02_INDEX));
  private static final TableName NAMESPACE02_TABLE03
          = TableName.valueOf(TEST_NAMESPACE_LIST.get(NAMESPACE02_INDEX),
                  TEST_TABLE_NAME_LIST.get(TABLE03_INDEX));
  private static final TableName NAMESPACE02_TABLE04
          = TableName.valueOf(TEST_NAMESPACE_LIST.get(NAMESPACE02_INDEX),
                  TEST_TABLE_NAME_LIST.get(TABLE04_INDEX));
  private static final TableName NAMESPACE03_TABLE01
          = TableName.valueOf(TEST_NAMESPACE_LIST.get(NAMESPACE03_INDEX),
                  TEST_TABLE_NAME_LIST.get(TABLE01_INDEX));
  private static final TableName NAMESPACE03_TABLE02
          = TableName.valueOf(TEST_NAMESPACE_LIST.get(NAMESPACE03_INDEX),
                  TEST_TABLE_NAME_LIST.get(TABLE02_INDEX));
  private static final TableName NAMESPACE03_TABLE03
          = TableName.valueOf(TEST_NAMESPACE_LIST.get(NAMESPACE03_INDEX),
                  TEST_TABLE_NAME_LIST.get(TABLE03_INDEX));
  private static final TableName NAMESPACE03_TABLE04
          = TableName.valueOf(TEST_NAMESPACE_LIST.get(NAMESPACE03_INDEX),
                  TEST_TABLE_NAME_LIST.get(TABLE04_INDEX));
  private static final byte[] CF01 = TEST_COLUMN_FAMILY_LIST.get(CF01_INDEX);
  private static final byte[] CF02 = TEST_COLUMN_FAMILY_LIST.get(CF02_INDEX);
  private static final byte[] COLQUALIFIER01 = TEST_COLUMN_QUALIFIER_LIST.get(0);
  private static final byte[] COLQUALIFIER02 = TEST_COLUMN_QUALIFIER_LIST.get(1);
  private static final byte[] COLQUALIFIER03 = TEST_COLUMN_QUALIFIER_LIST.get(2);
  private static final byte[] COLQUALIFIER04 = TEST_COLUMN_QUALIFIER_LIST.get(3);

  private static final Set<byte[]> expectedColQualifiersForNamespace1Table1Cf1
            = new TreeSet<>(Bytes.BYTES_RAWCOMPARATOR);
  static {
    expectedColQualifiersForNamespace1Table1Cf1.add(COLQUALIFIER01);
    expectedColQualifiersForNamespace1Table1Cf1.add(COLQUALIFIER02);
    expectedColQualifiersForNamespace1Table1Cf1.add(COLQUALIFIER03);
  }
  private static final Set<byte[]> expectedColQualifiersForNamespace1Table1Cf2
            = new TreeSet<>(Bytes.BYTES_RAWCOMPARATOR);
  static {
    expectedColQualifiersForNamespace1Table1Cf2.add(COLQUALIFIER04);
  }
  private static final Set<byte[]> expectedColQualifiersForNamespace3Table1Cf1
            = new TreeSet<>(Bytes.BYTES_RAWCOMPARATOR);
  static {
    expectedColQualifiersForNamespace3Table1Cf1.add(COLQUALIFIER01);
    expectedColQualifiersForNamespace3Table1Cf1.add(COLQUALIFIER03);
  }
  private static final Set<ColumnAuditor> expectedColAuditorsForNamespace1Table1Cf1 = new TreeSet<>();
  static {
    expectedColAuditorsForNamespace1Table1Cf1.add(
            new ColumnAuditor(COLQUALIFIER01).setMaxValueLengthFound(82));
    expectedColAuditorsForNamespace1Table1Cf1.add(
            new ColumnAuditor(COLQUALIFIER02).setMaxValueLengthFound(5));
    expectedColAuditorsForNamespace1Table1Cf1.add(
            new ColumnAuditor(COLQUALIFIER03).setMaxValueLengthFound(9));
  }
  private static final Set<ColumnAuditor> expectedColAuditorsForNamespace1Table1Cf2 = new TreeSet<>();
  static {
    expectedColAuditorsForNamespace1Table1Cf2.add(
            new ColumnAuditor(COLQUALIFIER04).setMaxValueLengthFound(82));
  }
  private static final Set<ColumnAuditor> expectedColAuditorsForNamespace3Table1Cf1 = new TreeSet<>();
  static {
    expectedColAuditorsForNamespace3Table1Cf1.add(
            new ColumnAuditor(COLQUALIFIER01).setMaxValueLengthFound(9));
    expectedColAuditorsForNamespace3Table1Cf1.add(
            new ColumnAuditor(COLQUALIFIER03).setMaxValueLengthFound(82));
  }
  private static final String ALTERNATE_USERNAME = "testAlternateUserName";

  private static final String TEST_ENVIRONMENT_SETUP_PROBLEM
          = "TEST ENVIRONMENT SETUP PROBLEM!! ==>> ";
  private static final String REPOSITORY_ADMIN_FAILURE
          = "FAILURE IN " + RepositoryAdmin.class.getSimpleName() + " PROCESSING!! ==>> ";
  private static final String COLUMN_AUDIT_FAILURE = "FAILURE IN Column Audit PROCESSING!! ==>> ";
  private static final String GET_COL_QUALIFIERS_FAILURE
          = COLUMN_AUDIT_FAILURE + "#getColumnQualifiers method returned unexpected results";
  private static final String GET_COL_AUDITORS_FAILURE
          = COLUMN_AUDIT_FAILURE + "#getColumnAuditors method returned unexpected results";
  private static final String COLUMN_ENFORCE_FAILURE
          = "FAILURE IN Column Enforce PROCESSING!! ==>> ";
  private static final String COL_QUALIFIER_ENFORCE_FAILURE
          = COLUMN_ENFORCE_FAILURE + "FAILURE IN enforcement of Column Qualifier";
  private static final String COL_LENGTH_ENFORCE_FAILURE
          = COLUMN_ENFORCE_FAILURE + "FAILURE IN enforcement of Column Length";
  private static final String COL_VALUE_ENFORCE_FAILURE
          = COLUMN_ENFORCE_FAILURE + "FAILURE IN enforcement of Column Value (regex)";
  private static final String HSA_FAILURE = "FAILURE IN HBase Schema Archive PROCESSING!! ==>> ";

  // non-static fields
  private Map<String, NamespaceDescriptor> testNamespacesAndDescriptors;
  private Map<TableName, HTableDescriptor> testTableNamesAndDescriptors;
  private Map<String, HColumnDescriptor> testColumnFamilyNamesAndDescriptors;
  private boolean usernameSuffix;

  @Test
  public void testStaticMethods() throws IOException {
    System.out.println("#testStaticMethods has been invoked.");
    try (Connection standardConnection = ConnectionFactory.createConnection();
            Admin standardAdmin = standardConnection.getAdmin()) {

      // do "manual" cleanup to prepare for unit test
      TestMConnectionFactory.manuallyDropRepositoryStructures(standardConnection, standardAdmin);

      RepositoryAdmin.installRepositoryStructures(standardAdmin);
      assertTrue(REPOSITORY_ADMIN_FAILURE
              + "The following Repository NAMESPACE failed to be created upon "
              + "invocation of #installRepositoryStructures method: "
              + Repository.REPOSITORY_NAMESPACE_DESCRIPTOR.getName(),
              TestMConnectionFactory.namespaceExists(
                      standardAdmin, Repository.REPOSITORY_NAMESPACE_DESCRIPTOR));
      assertTrue(REPOSITORY_ADMIN_FAILURE
              + "The following Repository TABLE failed to be created upon "
              + "invocation of #installRepositoryStructures method: "
              + Repository.REPOSITORY_TABLENAME.getNameAsString(),
              standardAdmin.tableExists(Repository.REPOSITORY_TABLENAME));

      assertEquals(REPOSITORY_ADMIN_FAILURE
              + "Incorrect default value for Repository maxVersions returned by "
              + "#getRepositoryMaxVersions method.",
              Repository.REPOSITORY_DEFAULT_MAX_VERSIONS,
              RepositoryAdmin.getRepositoryMaxVersions(standardAdmin));

      final int NEW_MAX_VERSIONS = 160;
      RepositoryAdmin.setRepositoryMaxVersions(standardAdmin, NEW_MAX_VERSIONS);
      assertEquals(REPOSITORY_ADMIN_FAILURE
              + "Incorrect value for Repository maxVersions returned by "
              + "#getRepositoryMaxVersions method following invocation of "
              + "#setRepositoryMaxVersions method.",
              NEW_MAX_VERSIONS,
              RepositoryAdmin.getRepositoryMaxVersions(standardAdmin));

      RepositoryAdmin.uninstallRepositoryStructures(standardAdmin);
      assertTrue(REPOSITORY_ADMIN_FAILURE
              + "The following Repository NAMESPACE failed to be dropped upon "
              + "invocation of #uninstallRepositoryStructures method: "
              + Repository.REPOSITORY_NAMESPACE_DESCRIPTOR.getName(),
              !TestMConnectionFactory.namespaceExists(
                      standardAdmin, Repository.REPOSITORY_NAMESPACE_DESCRIPTOR));
      assertTrue(REPOSITORY_ADMIN_FAILURE
              + "The following Repository TABLE failed to be dropped upon "
              + "invocation of #uninstallRepositoryStructures method: "
              + Repository.REPOSITORY_TABLENAME.getNameAsString(),
              !standardAdmin.tableExists(Repository.REPOSITORY_TABLENAME));
    }
    System.out.println("#testStaticMethods has run to completion.");
  }

  @Test
  public void testColumnAuditingWithWildcardedExcludes() throws IOException {
    System.out.println("#testColumnAuditing has been invoked using WILDCARDED "
            + "EXCLUDE config properties.");

    initializeTestNamespaceAndTableObjects();
    clearTestingEnvironment();

    // NOTE that test/resources/hbase-column-manager.xml contains wildcarded excludedTables entries
    Configuration configuration = MConfiguration.create();
    createSchemaStructuresInHBase(configuration, false);
    loadColumnData(configuration, false);
    verifyColumnAuditing(configuration);
    System.out.println("#testColumnAuditing using WILDCARDED EXCLUDE config properties has "
            + "run to completion.");
  }

  @Test
  public void testColumnAuditingWithExplicitExcludes() throws IOException {
    System.out.println("#testColumnAuditing has been invoked using EXPLICIT "
            + "EXCLUDE config properties.");

    initializeTestNamespaceAndTableObjects();
    clearTestingEnvironment();

    Configuration configuration = HBaseConfiguration.create();
    configuration.setBoolean(Repository.HBASE_CONFIG_PARM_KEY_COLMANAGER_ACTIVATED, true);
    configuration.setStrings(Repository.HBASE_CONFIG_PARM_KEY_COLMANAGER_EXCLUDED_TABLES,
            NAMESPACE03_TABLE02.getNameAsString(),
            NAMESPACE03_TABLE04.getNameAsString(),
            NAMESPACE02_TABLE01.getNameAsString(),
            NAMESPACE02_TABLE02.getNameAsString(),
            NAMESPACE02_TABLE03.getNameAsString(),
            NAMESPACE02_TABLE04.getNameAsString());
    createSchemaStructuresInHBase(configuration, false);
    loadColumnData(configuration, false);
    verifyColumnAuditing(configuration);
    System.out.println("#testColumnAuditing using EXPLICIT EXCLUDE config properties has "
            + "run to completion.");
  }

  @Test
  public void testColumnAuditingWithExplicitIncludes() throws IOException {
    System.out.println("#testColumnAuditing has been invoked using EXPLICIT "
            + "INCLUDE config properties.");

    initializeTestNamespaceAndTableObjects();
    clearTestingEnvironment();

    Configuration configuration = HBaseConfiguration.create();
    configuration.setBoolean(Repository.HBASE_CONFIG_PARM_KEY_COLMANAGER_ACTIVATED, true);

    // NOTE that the "include" settings added here are the inverse of the "exclude" settings
    //  in the hbase-column-manager.xml file in the test/resources directory. They should
    //  result in EXACTLY the same results in ColumnManager auditing.
    configuration.setStrings(Repository.HBASE_CONFIG_PARM_KEY_COLMANAGER_INCLUDED_TABLES,
            NAMESPACE03_TABLE01.getNameAsString(),
            NAMESPACE03_TABLE03.getNameAsString(),
            NAMESPACE01_TABLE01.getNameAsString(),
            NAMESPACE01_TABLE02.getNameAsString(),
            NAMESPACE01_TABLE03.getNameAsString(),
            NAMESPACE01_TABLE04.getNameAsString()
    );

    createSchemaStructuresInHBase(configuration, false);
    loadColumnData(configuration, false);
    verifyColumnAuditing(configuration);
    System.out.println("#testColumnAuditing using EXPLICIT INCLUDE config properties has "
            + "run to completion.");
  }

  @Test
  public void testColumnAuditingWithWildcardedIncludes() throws IOException {
    System.out.println("#testColumnAuditing has been invoked using WILDCARDED "
            + "INCLUDE config properties.");

    initializeTestNamespaceAndTableObjects();
    clearTestingEnvironment();

    Configuration configuration = HBaseConfiguration.create();
    configuration.setBoolean(Repository.HBASE_CONFIG_PARM_KEY_COLMANAGER_ACTIVATED, true);

    // NOTE that the "include" settings added here are the inverse of the "exclude" settings
    //  in the hbase-column-manager.xml file in the test/resources directory. They should
    //  result in EXACTLY the same results in ColumnManager auditing.
    configuration.setStrings(Repository.HBASE_CONFIG_PARM_KEY_COLMANAGER_INCLUDED_TABLES,
            NAMESPACE03_TABLE01.getNameAsString(),
            NAMESPACE03_TABLE03.getNameAsString(),
            TEST_NAMESPACE_LIST.get(NAMESPACE01_INDEX) + ":*"  // include all namespace01 tables!!
    );

    createSchemaStructuresInHBase(configuration, false);
    loadColumnData(configuration, false);
    verifyColumnAuditing(configuration);
    System.out.println("#testColumnAuditing using WILDCARDED INCLUDE config properties has "
            + "run to completion.");
  }

  @Test
  public void testColumnDiscoveryWithWildcardedExcludes() throws IOException {
    System.out.println("#testColumnDiscovery has been invoked using WILDCARDED "
            + "EXCLUDE config properties.");

    initializeTestNamespaceAndTableObjects();
    clearTestingEnvironment();

    // NOTE that test/resources/hbase-column-manager.xml contains wildcarded excludedTables entries
    Configuration configuration = MConfiguration.create();
    createSchemaStructuresInHBase(configuration, true);
    loadColumnData(configuration, true);
    doColumnDiscovery(configuration);
    verifyColumnAuditing(configuration);
    System.out.println("#testColumnDiscovery using WILDCARDED EXCLUDE config properties has "
            + "run to completion.");
  }

  @Test
  public void testColumnDefinitionAndEnforcement() throws IOException {
    System.out.println("#testColumnDefinitionAndEnforcement has been invoked using WILDCARDED "
            + "EXCLUDE config properties.");

    initializeTestNamespaceAndTableObjects();
    clearTestingEnvironment();

    // NOTE that test/resources/hbase-column-manager.xml contains wildcarded excludedTables entries
    Configuration configuration = MConfiguration.create();
    createSchemaStructuresInHBase(configuration, false);
    createAndEnforceColumnDefinitions(configuration);
    clearTestingEnvironment();

    System.out.println("#testColumnDefinitionAndEnforcement using WILDCARDED EXCLUDE config "
            + "properties has run to completion.");
  }

  private void clearTestingEnvironment() throws IOException {
    try (Connection standardConnection = ConnectionFactory.createConnection();
            Admin standardAdmin = standardConnection.getAdmin();
            RepositoryAdmin repositoryAdmin = new RepositoryAdmin(standardConnection)) {

      RepositoryAdmin.uninstallRepositoryStructures(standardAdmin);

      // loop to disable and drop test tables and namespaces
      for (TableName tableName : testTableNamesAndDescriptors.keySet()) {
        if (!standardAdmin.tableExists(tableName)) {
          continue;
        }
        standardAdmin.disableTable(tableName);
        standardAdmin.deleteTable(tableName);
      }
      for (String namespaceName : testNamespacesAndDescriptors.keySet()) {
        if (!repositoryAdmin.namespaceExists(namespaceName)) {
          continue;
        }
        standardAdmin.deleteNamespace(namespaceName);
      }
    }
  }

  private void initializeTestNamespaceAndTableObjects() {

    testNamespacesAndDescriptors = new TreeMap<>();
    testTableNamesAndDescriptors = new TreeMap<>();
    testColumnFamilyNamesAndDescriptors = new TreeMap<>();

    for (String namespace : TEST_NAMESPACE_LIST) {
      testNamespacesAndDescriptors.put(namespace, NamespaceDescriptor.create(namespace).build());
      for (String tableNameString : TEST_TABLE_NAME_LIST) {
        TableName tableName = TableName.valueOf(namespace, tableNameString);
        testTableNamesAndDescriptors.put(tableName, new HTableDescriptor(tableName));
      }
    }
    for (byte[] columnFamily : TEST_COLUMN_FAMILY_LIST) {
      testColumnFamilyNamesAndDescriptors.put(
              Bytes.toString(columnFamily), new HColumnDescriptor(columnFamily));
    }
  }

  private void createSchemaStructuresInHBase(
          Configuration configuration, boolean bypassColumnManager) throws IOException {
    int memStoreFlushSize = 60000000;
    int maxVersions = 8;
    boolean alternateBooleanAttribute = false;

    try (Admin mAdmin = MConnectionFactory.createConnection(configuration).getAdmin();
            Admin standardAdmin = ConnectionFactory.createConnection(configuration).getAdmin()) {
      for (NamespaceDescriptor nd : testNamespacesAndDescriptors.values()) {
        nd.setConfiguration("NamespaceConfigTest", "value=" + nd.getName());
        if (bypassColumnManager) {
          standardAdmin.createNamespace(nd);
        } else {
          mAdmin.createNamespace(nd);
        }
      }
      for (HTableDescriptor htd : testTableNamesAndDescriptors.values()) {
        htd.setMemStoreFlushSize(memStoreFlushSize++);
        htd.setDurability(Durability.SKIP_WAL);
        for (HColumnDescriptor hcd : testColumnFamilyNamesAndDescriptors.values()) {
          alternateBooleanAttribute = !alternateBooleanAttribute;
          hcd.setInMemory(alternateBooleanAttribute);
          hcd.setMaxVersions(maxVersions++);
          htd.addFamily(hcd);
        }
        if (bypassColumnManager) {
          standardAdmin.createTable(htd);
        } else {
          mAdmin.createTable(htd);
        }
      }
    }
  }

  private void loadColumnData(Configuration configuration, boolean bypassColumnManager)
          throws IOException {

    try (Connection mConnection = MConnectionFactory.createConnection(configuration);
            Connection standardConnection = ConnectionFactory.createConnection(configuration)) {

      Connection connection;
      if (bypassColumnManager) {
        connection = standardConnection;
      } else {
        connection = mConnection;
      }
      // put rows into Table which is INCLUDED for auditing
      try (Table table01InNamespace01 = connection.getTable(NAMESPACE01_TABLE01)) {
        List<Put> putList = new ArrayList<>();
        putList.add(new Put(ROW_ID_01).
                addColumn(CF01, COLQUALIFIER01, VALUE_2_BYTES_LONG).
                addColumn(CF01, COLQUALIFIER02, VALUE_5_BYTES_LONG));
        putList.add(new Put(ROW_ID_02).
                addColumn(CF01, COLQUALIFIER01, VALUE_82_BYTES_LONG).
                addColumn(CF01, COLQUALIFIER03, VALUE_9_BYTES_LONG).
                addColumn(CF02, COLQUALIFIER04, VALUE_82_BYTES_LONG));
        table01InNamespace01.put(putList);
      }

      try (Table table01InNamespace03 = connection.getTable(NAMESPACE03_TABLE01)) {

        List<Put> putList = new ArrayList<>();
        putList.add(new Put(ROW_ID_04).
                addColumn(CF01, COLQUALIFIER03, VALUE_82_BYTES_LONG).
                addColumn(CF01, COLQUALIFIER01, VALUE_9_BYTES_LONG));
        table01InNamespace03.put(putList);
      }

      // put two rows into Table in Namespace which is NOT included for ColumnManager auditing
      try (Table table01InNamespace02 = connection.getTable(NAMESPACE02_TABLE01)) {

        List<Put> putList = new ArrayList<>();
        putList.add(new Put(ROW_ID_01).
                addColumn(CF01, QUALIFIER_IN_EXCLUDED_TABLE, VALUE_2_BYTES_LONG).
                addColumn(CF02, QUALIFIER_IN_EXCLUDED_TABLE, VALUE_82_BYTES_LONG));
        putList.add(new Put(ROW_ID_02).
                addColumn(CF01, QUALIFIER_IN_EXCLUDED_TABLE, VALUE_9_BYTES_LONG).
                addColumn(CF02, QUALIFIER_IN_EXCLUDED_TABLE, VALUE_82_BYTES_LONG));
        table01InNamespace02.put(putList);
      }

      // put one row into Table which is explicitly NOT included for ColumnManager auditing
      try (Table table02InNamespace03 = connection.getTable(NAMESPACE03_TABLE02)) {

        List<Put> putList = new ArrayList<>();
        putList.add(new Put(ROW_ID_03).
                addColumn(CF01, QUALIFIER_IN_EXCLUDED_TABLE, VALUE_9_BYTES_LONG).
                addColumn(CF02, QUALIFIER_IN_EXCLUDED_TABLE, VALUE_5_BYTES_LONG));
        table02InNamespace03.put(putList);
      }
    }
  }

  private void doColumnDiscovery(Configuration configuration) throws IOException {
    try (RepositoryAdmin repositoryAdmin
            = new RepositoryAdmin(MConnectionFactory.createConnection(configuration))) {
      repositoryAdmin.discoverSchema();
    }
  }

  private void verifyColumnAuditing(Configuration configuration) throws IOException {

    try (Connection mConnection = MConnectionFactory.createConnection(configuration);
            RepositoryAdmin repositoryAdmin = new RepositoryAdmin(mConnection)) {

      // Test #getColumnQualifiers
      Set<byte[]> returnedColQualifiersForNamespace1Table1Cf1
              = repositoryAdmin.getColumnQualifiers(
                      testTableNamesAndDescriptors.get(NAMESPACE01_TABLE01),
                      testColumnFamilyNamesAndDescriptors.get(Bytes.toString(CF01)));
      assertTrue(GET_COL_QUALIFIERS_FAILURE,
              expectedColQualifiersForNamespace1Table1Cf1.equals(
                      returnedColQualifiersForNamespace1Table1Cf1));

      Set<byte[]> returnedColQualifiersForNamespace1Table1Cf2
              = repositoryAdmin.getColumnQualifiers(
                      testTableNamesAndDescriptors.get(NAMESPACE01_TABLE01),
                      testColumnFamilyNamesAndDescriptors.get(Bytes.toString(CF02)));
      assertTrue(GET_COL_QUALIFIERS_FAILURE,
              expectedColQualifiersForNamespace1Table1Cf2.equals(
                      returnedColQualifiersForNamespace1Table1Cf2));

      Set<byte[]> returnedColQualifiersForNamespace2Table1Cf1
              = repositoryAdmin.getColumnQualifiers(
                      testTableNamesAndDescriptors.get(NAMESPACE02_TABLE01),
                      testColumnFamilyNamesAndDescriptors.get(Bytes.toString(CF01)));
      assertTrue(GET_COL_QUALIFIERS_FAILURE, returnedColQualifiersForNamespace2Table1Cf1 == null);

      Set<byte[]> returnedColQualifiersForNamespace3Table1Cf1
              = repositoryAdmin.getColumnQualifiers(
                      testTableNamesAndDescriptors.get(NAMESPACE03_TABLE01),
                      testColumnFamilyNamesAndDescriptors.get(Bytes.toString(CF01)));
      assertTrue(GET_COL_QUALIFIERS_FAILURE,
              expectedColQualifiersForNamespace3Table1Cf1.equals(
                      returnedColQualifiersForNamespace3Table1Cf1));

      Set<byte[]> returnedColQualifiersForNamespace3Table2Cf1
              = repositoryAdmin.getColumnQualifiers(
                      testTableNamesAndDescriptors.get(NAMESPACE03_TABLE02),
                      testColumnFamilyNamesAndDescriptors.get(Bytes.toString(CF01)));
      assertTrue(GET_COL_QUALIFIERS_FAILURE, returnedColQualifiersForNamespace3Table2Cf1 == null);

      // Test #getColumnQualifiers with alternate signature
      returnedColQualifiersForNamespace1Table1Cf1
              = repositoryAdmin.getColumnQualifiers(NAMESPACE01_TABLE01, CF01);
      assertTrue(GET_COL_QUALIFIERS_FAILURE,
              expectedColQualifiersForNamespace1Table1Cf1.equals(
                      returnedColQualifiersForNamespace1Table1Cf1));

      returnedColQualifiersForNamespace1Table1Cf2
              = repositoryAdmin.getColumnQualifiers(NAMESPACE01_TABLE01, CF02);
      assertTrue(GET_COL_QUALIFIERS_FAILURE,
              expectedColQualifiersForNamespace1Table1Cf2.equals(
                      returnedColQualifiersForNamespace1Table1Cf2));

      returnedColQualifiersForNamespace2Table1Cf1
              = repositoryAdmin.getColumnQualifiers(NAMESPACE02_TABLE01, CF01);
      assertTrue(GET_COL_QUALIFIERS_FAILURE, returnedColQualifiersForNamespace2Table1Cf1 == null);

      returnedColQualifiersForNamespace3Table2Cf1
              = repositoryAdmin.getColumnQualifiers(NAMESPACE03_TABLE02, CF01);
      assertTrue(GET_COL_QUALIFIERS_FAILURE, returnedColQualifiersForNamespace3Table2Cf1 == null);

      // Test #getColumnAuditors
      Set<ColumnAuditor> returnedColAuditorsForNamespace1Table1Cf1
              = repositoryAdmin.getColumnAuditors(
                      testTableNamesAndDescriptors.get(NAMESPACE01_TABLE01),
                      testColumnFamilyNamesAndDescriptors.get(Bytes.toString(CF01)));
      assertTrue(GET_COL_AUDITORS_FAILURE,
              expectedColAuditorsForNamespace1Table1Cf1.equals(
                      returnedColAuditorsForNamespace1Table1Cf1));

      Set<ColumnAuditor> returnedColAuditorsForNamespace1Table1Cf2
              = repositoryAdmin.getColumnAuditors(
                      testTableNamesAndDescriptors.get(NAMESPACE01_TABLE01),
                      testColumnFamilyNamesAndDescriptors.get(Bytes.toString(CF02)));
      assertTrue(GET_COL_AUDITORS_FAILURE,
              expectedColAuditorsForNamespace1Table1Cf2.equals(
                      returnedColAuditorsForNamespace1Table1Cf2));

      Set<ColumnAuditor> returnedColAuditorsForNamespace2Table1Cf1
              = repositoryAdmin.getColumnAuditors(
                      testTableNamesAndDescriptors.get(NAMESPACE02_TABLE01),
                      testColumnFamilyNamesAndDescriptors.get(Bytes.toString(CF01)));
      assertTrue(GET_COL_AUDITORS_FAILURE, returnedColAuditorsForNamespace2Table1Cf1 == null);

      Set<ColumnAuditor> returnedColAuditorsForNamespace3Table1Cf1
              = repositoryAdmin.getColumnAuditors(
                      testTableNamesAndDescriptors.get(NAMESPACE03_TABLE01),
                      testColumnFamilyNamesAndDescriptors.get(Bytes.toString(CF01)));
      assertTrue(GET_COL_AUDITORS_FAILURE,
              expectedColAuditorsForNamespace3Table1Cf1.equals(
                      returnedColAuditorsForNamespace3Table1Cf1));

      Set<ColumnAuditor> returnedColAuditorsForNamespace3Table2Cf1
              = repositoryAdmin.getColumnAuditors(
                      testTableNamesAndDescriptors.get(NAMESPACE03_TABLE02),
                      testColumnFamilyNamesAndDescriptors.get(Bytes.toString(CF01)));
      assertTrue(GET_COL_AUDITORS_FAILURE, returnedColAuditorsForNamespace3Table2Cf1 == null);

      // Test #getColumnAuditors with alternate signature
      returnedColAuditorsForNamespace1Table1Cf1
              = repositoryAdmin.getColumnAuditors(NAMESPACE01_TABLE01, CF01);
      assertTrue(GET_COL_AUDITORS_FAILURE,
              expectedColAuditorsForNamespace1Table1Cf1.equals(
                      returnedColAuditorsForNamespace1Table1Cf1));

      returnedColAuditorsForNamespace1Table1Cf2
              = repositoryAdmin.getColumnAuditors(NAMESPACE01_TABLE01, CF02);
      assertTrue(GET_COL_AUDITORS_FAILURE,
              expectedColAuditorsForNamespace1Table1Cf2.equals(
                      returnedColAuditorsForNamespace1Table1Cf2));

      returnedColAuditorsForNamespace2Table1Cf1
              = repositoryAdmin.getColumnAuditors(NAMESPACE02_TABLE01, CF01);
      assertTrue(GET_COL_AUDITORS_FAILURE, returnedColAuditorsForNamespace2Table1Cf1 == null);

      returnedColAuditorsForNamespace3Table2Cf1
              = repositoryAdmin.getColumnAuditors(NAMESPACE03_TABLE02, CF01);
      assertTrue(GET_COL_AUDITORS_FAILURE, returnedColAuditorsForNamespace3Table2Cf1 == null);
    }
    clearTestingEnvironment();
  }

  private void createAndEnforceColumnDefinitions(Configuration configuration) throws IOException {
    ColumnDefinition col01Definition = new ColumnDefinition(COLQUALIFIER01);
    ColumnDefinition col02Definition = new ColumnDefinition(COLQUALIFIER02).setColumnLength(20L);
    ColumnDefinition col03Definition
            = new ColumnDefinition(COLQUALIFIER03).setColumnValidationRegex("https?://.*");
    ColumnDefinition col04Definition
            = new ColumnDefinition(COLQUALIFIER04).setColumnLength(8L);

    try (Connection connection = MConnectionFactory.createConnection(configuration);
            RepositoryAdmin repositoryAdmin = new RepositoryAdmin(connection)) {
      repositoryAdmin.addColumnDefinition(NAMESPACE01_TABLE01, CF01, col01Definition);
      repositoryAdmin.addColumnDefinition(NAMESPACE01_TABLE01, CF01, col02Definition);
      repositoryAdmin.addColumnDefinition(NAMESPACE01_TABLE01, CF02, col03Definition);
      // next def not enforced, since namespace02 tables not included for CM processing!
      repositoryAdmin.addColumnDefinition(NAMESPACE02_TABLE03, CF01, col04Definition);

      repositoryAdmin.setColumnDefinitionsEnforced(true, NAMESPACE01_TABLE01, CF01);
      repositoryAdmin.setColumnDefinitionsEnforced(true, NAMESPACE01_TABLE01, CF02);
       // next def not enforced, since namespace02 tables not included for CM processing!
      repositoryAdmin.setColumnDefinitionsEnforced(true, NAMESPACE02_TABLE03, CF01);

      try (Table table01InNamespace01 = connection.getTable(NAMESPACE01_TABLE01);
              Table table03InNamespace02 = connection.getTable(NAMESPACE02_TABLE03)) {
        // put a row with valid columns
        table01InNamespace01.put(new Put(ROW_ID_01).
                addColumn(CF01, COLQUALIFIER01, VALUE_2_BYTES_LONG).
                addColumn(CF01, COLQUALIFIER02, VALUE_5_BYTES_LONG));
        // put a row with invalid column qualifier
        try {
          table01InNamespace01.put(new Put(ROW_ID_01).
                  addColumn(CF01, COLQUALIFIER03, VALUE_2_BYTES_LONG));
          fail(COL_QUALIFIER_ENFORCE_FAILURE);
        } catch (ColumnDefinitionNotFoundException e) {
        }
        // put same row to unenforced namespace/table
        table03InNamespace02.put(new Put(ROW_ID_01).
                addColumn(CF01, COLQUALIFIER03, VALUE_2_BYTES_LONG));
        // put a row with invalid column length
        try {
          table01InNamespace01.put(new Put(ROW_ID_01).
                  addColumn(CF01, COLQUALIFIER02, VALUE_82_BYTES_LONG));
          fail(COL_LENGTH_ENFORCE_FAILURE);
        } catch (InvalidColumnValueException e) {
        }
        // put same row to unenforced namespace/table
        table03InNamespace02.put(new Put(ROW_ID_01).
                addColumn(CF01, COLQUALIFIER02, VALUE_82_BYTES_LONG));
        // put a row with valid column value to regex-restricted column
        table01InNamespace01.put(new Put(ROW_ID_01).
                addColumn(CF02, COLQUALIFIER03, Bytes.toBytes("http://google.com")));
        // put a row with invalid column value
        try {
          table01InNamespace01.put(new Put(ROW_ID_01).
                  addColumn(CF02, COLQUALIFIER03, Bytes.toBytes("ftp://google.com")));
          fail(COL_VALUE_ENFORCE_FAILURE);
        } catch (InvalidColumnValueException e) {
          // this Exception SHOULD be thrown!
        }
        // put same row to unenforced namespace/table
        table03InNamespace02.put(new Put(ROW_ID_01).
                addColumn(CF02, COLQUALIFIER03, Bytes.toBytes("ftp://google.com")));
      }
    }
  }

  @Rule
  public TemporaryFolder tempTestFolder = new TemporaryFolder();

  @Test
  public void testExportImport() throws IOException, JAXBException {
    System.out.println("#testExportImport has been invoked.");
    // file setup
    final String TARGET_DIRECTORY = "target/"; // for standalone (non-JUnit) execution
    final String TARGET_EXPORT_ALL_FILE = "temp.export.repository.hsa.xml";
    final String TARGET_EXPORT_NAMESPACE_FILE = "temp.export.namespace.hsa.xml";
    final String TARGET_EXPORT_TABLE_FILE = "temp.export.table.hsa.xml";
    File exportAllFile;
    File exportNamespaceFile;
    File exportTableFile;
    try {
      exportAllFile = tempTestFolder.newFile(TARGET_EXPORT_ALL_FILE);
      exportNamespaceFile = tempTestFolder.newFile(TARGET_EXPORT_NAMESPACE_FILE);
      exportTableFile = tempTestFolder.newFile(TARGET_EXPORT_TABLE_FILE);
    } catch (IllegalStateException e) { // standalone (non-JUnit) execution
      exportAllFile = new File(TARGET_DIRECTORY + TARGET_EXPORT_ALL_FILE);
      exportNamespaceFile = new File(TARGET_DIRECTORY + TARGET_EXPORT_NAMESPACE_FILE);
      exportTableFile = new File(TARGET_DIRECTORY + TARGET_EXPORT_TABLE_FILE);
    }
    // environment cleanup before testing
    initializeTestNamespaceAndTableObjects();
    clearTestingEnvironment();

    // add schema and data to HBase
    // NOTE that test/resources/hbase-column-manager.xml contains wildcarded excludedTables entries
    Configuration configuration = MConfiguration.create();
    createSchemaStructuresInHBase(configuration, false);
    loadColumnData(configuration, false);

    // extract schema into external HBase Schema Archive files
    try (RepositoryAdmin repositoryAdmin
            = new RepositoryAdmin(MConnectionFactory.createConnection(configuration))) {
      repositoryAdmin.exportRepository(exportAllFile, true);
      repositoryAdmin.exportNamespaceSchema(
              TEST_NAMESPACE_LIST.get(NAMESPACE01_INDEX), exportNamespaceFile, true);
      repositoryAdmin.exportTableSchema(NAMESPACE01_TABLE01, exportTableFile, true);
    }
    clearTestingEnvironment();

    // NOW restore full schema from external HSA file and verify that all structures restored
    try (RepositoryAdmin repositoryAdmin
            = new RepositoryAdmin(MConnectionFactory.createConnection(configuration))) {
      repositoryAdmin.importSchema(true, exportAllFile);
    }
    verifyColumnAuditing(configuration);

    // validate all export files against the XML-schema
    validateXmlAgainstXsd(exportAllFile);
    validateXmlAgainstXsd(exportNamespaceFile);
    validateXmlAgainstXsd(exportTableFile);

    // assure appropriate content in Namespace and Table archive files!!
    HBaseSchemaArchive repositoryArchive = HBaseSchemaArchive.deserializeXmlFile(exportAllFile);
    HBaseSchemaArchive namespaceArchive
            = HBaseSchemaArchive.deserializeXmlFile(exportNamespaceFile);
    HBaseSchemaArchive tableArchive = HBaseSchemaArchive.deserializeXmlFile(exportTableFile);
    for (SchemaEntity entity : repositoryArchive.getSchemaEntities()) {
      if (entity.getEntityRecordType() == SchemaEntityType.NAMESPACE.getRecordType()
              && entity.getNameAsString().equals(TEST_NAMESPACE_LIST.get(NAMESPACE01_INDEX))) {
        assertEquals(HSA_FAILURE + "Namespace SchemaEntity inconsistencies between full archive "
                + "and namespace-only archive.",
                entity, namespaceArchive.getSchemaEntities().iterator().next());
        for (SchemaEntity childEntity : entity.getChildren()) {
          if (childEntity.getEntityRecordType() == SchemaEntityType.TABLE.getRecordType()
                  && childEntity.getNameAsString().equals(NAMESPACE01_TABLE01.getNameAsString())) {
            SchemaEntity namespaceEntityInTableArchive
                    = tableArchive.getSchemaEntities().iterator().next();
            assertEquals(HSA_FAILURE + "Namespace ShemaEntity in Table Archive has unexpected "
                    + "size of children Set.",
                    1, namespaceEntityInTableArchive.getChildren().size());
            assertEquals(HSA_FAILURE + "Table SchemaEntity inconsistencies between full archive "
                    + "and table-only archive.",
                    childEntity, namespaceEntityInTableArchive.getChildren().iterator().next());
          }
        }
      }
    }
    System.out.println("#testExportImport has run to completeion.");
  }

  private void validateXmlAgainstXsd(File xmlFile) throws IOException {
    Document hsaDocument = null;
    Schema hsaSchema = null;
    try {
      hsaDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile);
    } catch (ParserConfigurationException pce) {
      fail(TEST_ENVIRONMENT_SETUP_PROBLEM + " parser config exception thrown: " + pce.getMessage());
    } catch (SAXException se) {
      fail(REPOSITORY_ADMIN_FAILURE + " SAX exception thrown while loading test document: "
              + se.getMessage());
    }
    try {
      hsaSchema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(
              Paths.get(ClassLoader.getSystemResource(
                      XmlSchemaGenerator.DEFAULT_OUTPUT_FILE_NAME).toURI()).toFile());
    } catch (URISyntaxException ue) {
      fail(TEST_ENVIRONMENT_SETUP_PROBLEM + " URI syntax exception thrown: " + ue.getMessage());
    } catch (SAXException se) {
      fail(REPOSITORY_ADMIN_FAILURE + " SAX exception thrown while loading XML-schema: "
              + se.getMessage());
    }
    // validate against XSD
    try {
      hsaSchema.newValidator().validate(new DOMSource(hsaDocument));
    } catch (SAXException se) {
      fail(REPOSITORY_ADMIN_FAILURE + " exported HSA file is invalid with respect to "
              + "XML schema: " + se.getMessage());
    }
  }

  @Test
  public void testChangeEventMonitor() throws IOException {
    System.out.println("#testChangeEventMonitor has been invoked.");
    // file setup
    final String TARGET_DIRECTORY = "target/"; // for standalone (non-JUnit) execution
    final String TARGET_EXPORT_ALL_BY_TIMESTAMP_FILE = "temp.changeEvents.timestampOrder.csv";
    final String TARGET_EXPORT_ALL_BY_USERNAME_FILE = "temp.changeEvents.userNameOrder.csv";
    final String TARGET_EXPORT_FOR_USER_FILE = "temp.changeEvents.forUser.csv";
    final String TARGET_EXPORT_FOR_TABLE_FILE = "temp.changeEvents.forTable.csv";
    File exportAllByTimestampFile;
    File exportAllByUsernameFile;
    File exportForUserFile;
    File exportForTableFile;
    try {
      exportAllByTimestampFile = tempTestFolder.newFile(TARGET_EXPORT_ALL_BY_TIMESTAMP_FILE);
      exportAllByUsernameFile = tempTestFolder.newFile(TARGET_EXPORT_ALL_BY_USERNAME_FILE);
      exportForUserFile = tempTestFolder.newFile(TARGET_EXPORT_FOR_USER_FILE);
      exportForTableFile = tempTestFolder.newFile(TARGET_EXPORT_FOR_TABLE_FILE);
    } catch (IllegalStateException e) { // standalone (non-JUnit) execution
      exportAllByTimestampFile = new File(TARGET_DIRECTORY + TARGET_EXPORT_ALL_BY_TIMESTAMP_FILE);
      exportAllByUsernameFile = new File(TARGET_DIRECTORY + TARGET_EXPORT_ALL_BY_USERNAME_FILE);
      exportForUserFile = new File(TARGET_DIRECTORY + TARGET_EXPORT_FOR_USER_FILE);
      exportForTableFile = new File(TARGET_DIRECTORY + TARGET_EXPORT_FOR_TABLE_FILE);
    }
    // environment cleanup before testing
    initializeTestNamespaceAndTableObjects();
    clearTestingEnvironment();

    // add schema and data to HBase
    // NOTE that test/resources/hbase-column-manager.xml contains wildcarded excludedTables entries
    Configuration configuration = MConfiguration.create();
    changeJavaUsername();
    createSchemaStructuresInHBase(configuration, false);
    changeJavaUsername();
    createAndEnforceColumnDefinitions(configuration);
    deleteTableInHBase(configuration);

    // create and test ChangeEventMonitor
    try (RepositoryAdmin repositoryAdmin
            = new RepositoryAdmin(MConnectionFactory.createConnection(configuration))) {
      ChangeEventMonitor monitor = repositoryAdmin.getChangeEventMonitor();

      List<ChangeEvent> allChangeEvents = monitor.getAllChangeEvents();
      ChangeEventMonitor.exportChangeEventListToCsvFile(allChangeEvents, exportAllByTimestampFile);

      List<ChangeEvent> allChangeEventsByUsername = monitor.getAllChangeEventsByUserName();
      ChangeEventMonitor.exportChangeEventListToCsvFile(
              allChangeEventsByUsername, exportAllByUsernameFile);

      List<ChangeEvent> allChangeEventsOfSpecificUser
              = monitor.getChangeEventsForUserName("userfalse");
      ChangeEventMonitor.exportChangeEventListToCsvFile(
              allChangeEventsOfSpecificUser, exportForUserFile);

    }
    clearTestingEnvironment();
    System.out.println("#testChangeEventMonitor has run to completion.");
  }

  private void changeJavaUsername() {
    usernameSuffix = !usernameSuffix;
    System.setProperty("user.name", "user" + usernameSuffix);
  }

  private void deleteTableInHBase(Configuration configuration) throws IOException {
    try (Admin mAdmin = MConnectionFactory.createConnection(configuration).getAdmin()) {
      mAdmin.disableTable(NAMESPACE01_TABLE01);
      mAdmin.deleteTable(NAMESPACE01_TABLE01);
    }
  }

  public static void main(String[] args) throws Exception {
    // new TestRepositoryAdmin().testStaticMethods();
    // new TestRepositoryAdmin().testColumnDiscoveryWithWildcardedExcludes();
    // new TestRepositoryAdmin().testColumnAuditingWithWildcardedExcludes();
    // new TestRepositoryAdmin().testColumnAuditingWithExplicitIncludes();
    // new TestRepositoryAdmin().testColumnDefinitionAndEnforcement();
    // new TestRepositoryAdmin().testExportImport();
    new TestRepositoryAdmin().testChangeEventMonitor();
  }
}
