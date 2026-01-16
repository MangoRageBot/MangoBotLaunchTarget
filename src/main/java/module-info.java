module org.mangorage.mangobotlaunchtarget {
    requires org.mangorage.bootstrap;

    exports org.mangorage.mangobotlaunch.launch;

    provides org.mangorage.mangobotlaunch.launch.MangoBotLaunchTarget with org.mangorage.mangobotlaunch.launch.MangoBotLaunchTarget;
    provides org.mangorage.mangobotlaunch.launch.MangoBotDependencyLocator with org.mangorage.mangobotlaunch.launch.MangoBotDependencyLocator;

    uses org.mangorage.bootstrap.api.launch.ILaunchTarget;
    uses org.mangorage.bootstrap.api.dependency.IDependencyLocator;
}