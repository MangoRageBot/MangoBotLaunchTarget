module org.mangorage.mangobotlaunchtarget {
    requires org.mangorage.bootstrap;

    opens org.mangorage.mangobotlaunch.launch;
    opens org.mangorage.mangobotlaunch.util;

    provides org.mangorage.bootstrap.api.launch.ILaunchTarget with org.mangorage.mangobotlaunch.launch.MangoBotLaunchTarget;
    provides org.mangorage.bootstrap.api.dependency.IDependencyLocator with org.mangorage.mangobotlaunch.launch.MangoBotDependencyLocator;

    uses org.mangorage.bootstrap.api.launch.ILaunchTarget;
    uses org.mangorage.bootstrap.api.launch.ILaunchTargetEntrypoint;
    uses org.mangorage.bootstrap.api.dependency.IDependencyLocator;
    uses org.mangorage.bootstrap.api.module.IModuleConfigurator;
    uses org.mangorage.bootstrap.api.transformer.IClassTransformer;
}