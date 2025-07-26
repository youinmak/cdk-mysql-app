package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.dms.CfnEndpoint;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.rds.*;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.List;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class CdkMysqlAppStack extends Stack {
    public CdkMysqlAppStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public CdkMysqlAppStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here

        // example resource
        // final Queue queue = Queue.Builder.create(this, "CdkMysqlAppQueue")
        //         .visibilityTimeout(Duration.seconds(300))
        //         .build();

        List<String> azs = List.of("ap-south-1a");

        VpcAttributes vpcAttributes = VpcAttributes.builder()
                .availabilityZones(azs)
                .vpcId("vpc-0305d52c08946d8a4")
                .build();
        IVpc vpc = Vpc.fromVpcAttributes(this, "ExistingVPC", vpcAttributes);

        // subnet-0a48dbcbbccf5a2a7, subnet-0257b373485708fa4, subnet-0a4aa7a845dd01612

        List<String> subnetIds = new ArrayList<>();
        subnetIds.add("subnet-0a48dbcbbccf5a2a7");
        subnetIds.add("subnet-0257b373485708fa4");
        subnetIds.add("subnet-0a4aa7a845dd01612");

        List<ISubnet> subnets = new ArrayList<>();

        for (String subnetId : subnetIds) {
            SubnetAttributes subnetAttributes = SubnetAttributes.builder()
                    .subnetId(subnetId)
                    .build();
            ISubnet iSubnet = Subnet.fromSubnetAttributes(this, subnetId, subnetAttributes);
            subnets.add(iSubnet);
        }

        SubnetSelection vpcSubnets = SubnetSelection.builder().subnets(subnets).build();

        Port allAll = Port.allTraffic();
        Port tcp3306 = Port.tcpRange(3306, 3306);

        SecurityGroupProps securityGroupProps = SecurityGroupProps.builder()
                .vpc(vpc)
                .securityGroupName(id + "Database")
                .description(id + "Database")
                .allowAllOutbound(true)
                .build();

        ISecurityGroup dbsg = new SecurityGroup(this, "DatabaseSecurityGroup", securityGroupProps);
        dbsg.addIngressRule(dbsg, allAll, "all from self");
        dbsg.addEgressRule(Peer.ipv4("0.0.0.0/0"), allAll, "all out");

        SubnetGroupProps subnetGroupProps = SubnetGroupProps.builder()
                .vpc(vpc)
                .vpcSubnets(vpcSubnets)
                .description(id + "subnet group")
                .subnetGroupName(id + "subnet group")
                .build();

        SubnetGroup dbSubnetGroup = new SubnetGroup(this, "DatabaseSubnetGroup", subnetGroupProps);

        Credentials credentials = Credentials.fromPassword("dbadmin", SecretValue.unsafePlainText("nimdabd123"));

        IInstanceEngine mysqled = DatabaseInstanceEngine.mysql(MySqlInstanceEngineProps.builder()
                .version(MysqlEngineVersion.VER_8_0_42)
                .build());

        ParameterGroupProps parameterGroupProps = ParameterGroupProps.builder()
                .engine(mysqled)
                .build();
        ParameterGroup pg = new ParameterGroup(this, "ParameterGroup", parameterGroupProps);

        DatabaseInstanceProps dbInstanceProps = DatabaseInstanceProps.builder()
                .databaseName("sampledb")
                .instanceIdentifier("sampledb")
                .credentials(credentials)
                .engine(mysqled)
                .port(3306)
                .multiAz(false)
                .storageType(StorageType.GP2)
                //.licenseModel(LicenseModel.GENERAL_PUBLIC_LICENSE)
                .enablePerformanceInsights(false)
                .deleteAutomatedBackups(true)
                .backupRetention(Duration.days(1))
                .allocatedStorage(10)
                .securityGroups(List.of(dbsg))
                .allowMajorVersionUpgrade(false)
                .autoMinorVersionUpgrade(true)
                .instanceType(InstanceType.of(InstanceClass.T4G, InstanceSize.MICRO))
                .vpcSubnets(vpcSubnets)
                .vpc(vpc)
                .removalPolicy(RemovalPolicy.DESTROY)
                .storageEncrypted(false)
                .monitoringInterval(Duration.seconds(60))
                .parameterGroup(pg)
                .subnetGroup(dbSubnetGroup)
                .preferredBackupWindow("00:15-01:15")
                .preferredMaintenanceWindow("Sun:23:45-Mon:00:15")
                .publiclyAccessible(true)
                .build();
        DatabaseInstance mysqlInstance = new DatabaseInstance(this, "MysqlDatabase", dbInstanceProps);

       // mysqlInstance.addRotationSingleUser();

        Tags.of(mysqlInstance).add("Name", "MysqlDatabase", TagProps.builder()
                .priority(300)
                .build());

        new CfnOutput(this, "MysqlEndpoint", CfnOutputProps.builder()
                .exportName("MysqlEndpoint")
                .value(mysqlInstance.getDbInstanceEndpointAddress())
                .build());

        new CfnOutput(this, "MysqlUserName", CfnOutputProps.builder()
                .exportName("MysqlUserName")
                .value("dbadmin")
                .build());

        new CfnOutput(this, "MysqlDbName", CfnOutputProps.builder()
                .exportName("MysqlDbName")
                .value("sampledb")
                .build());
    }
}
