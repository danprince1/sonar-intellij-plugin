package org.intellij.sonar.javaassist;

@SuppressWarnings("UnusedDeclaration")
public class Test {
  /*public static void main(String[] args) throws IllegalAccessException, InstantiationException {
    List<SonarSettingsBean> sonarSettingsBeans = new ArrayList<SonarSettingsBean>(3);
    sonarSettingsBeans.add(new SonarSettingsBean("http://localhost:9000", "admin", "admin", "java:groovy:project"));
    sonarSettingsBeans.add(new SonarSettingsBean("http://localhost:9000", "admin", "admin", "java:groovy:project:java"));
    sonarSettingsBeans.add(new SonarSettingsBean("http://localhost:9000", "admin", "admin", "java:groovy:project:groovy"));

    SonarServer sonarServer = new SonarServer();
    Collection<Rule> allRules = sonarServer.getAllRules(sonarSettingsBeans, new CommandLineProgress());
    List<Class<SonarLocalInspectionTool>> classes = new ArrayList<Class<SonarLocalInspectionTool>>(allRules.size());
    for (Rule rule : allRules) {
      classes.add(getSonarLocalInspectionToolForOneRule(rule));
    }
    for (Class clazz : classes) {
      SonarLocalInspectionTool sonarLocalInspectionTool = (SonarLocalInspectionTool) clazz.newInstance();
//            System.out.println(sonarLocalInspectionTool.getDisplayName() + " : " + sonarLocalInspectionTool.getShortName() + " : " + sonarLocalInspectionTool.getStaticDescription());
      System.out.println(clazz.getName());
    }
  }

  private static Class<SonarLocalInspectionTool> getSonarLocalInspectionToolForOneRule(final Rule rule) throws IllegalAccessException, InstantiationException {
    ProxyFactory f = new ProxyFactory();
    f.setSuperclass(SonarLocalInspectionTool.class);
    f.setFilter(new MethodFilter() {
      @Override
      public boolean isHandled(Method method) {
        return method.getName().equals("getDisplayName")
            || method.getName().equals("getStaticDescription")
            || method.getName().equals("getShortName")
            || method.getName().equals("getRuleKey");
      }
    });
    //noinspection deprecation
    f.setHandler(new MethodHandler() {
      String myDisplayName = rule.getTitle();
      String myStaticDescription = rule.getDescription();
      String myShortName = rule.getKey();
      String myRuleKey = rule.getKey();

      @Override
      public Object invoke(Object o, Method method, Method method2, Object[] objects) throws Throwable {
        if (method.getName().equals("getDisplayName")) {
          return myDisplayName;
        } else if (method.getName().equals("getStaticDescription")) {
          return myStaticDescription;
        } else if (method.getName().equals("getShortName")) {
          if (StringUtils.isNotBlank(myShortName)) {
            myShortName = myShortName.replaceAll("\\s", "");
          }
          return myShortName;
        } else if (method.getName().equals("getRuleKey")) {
          return myRuleKey;
        } else {
          return null;
        }
      }
    });

    //noinspection unchecked
    return f.createClass();
  }*/
}
