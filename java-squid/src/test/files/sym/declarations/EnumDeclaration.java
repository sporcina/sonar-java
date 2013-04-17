package declarations;

@SuppressWarnings("UnusedDeclaration")
class EnumDeclaration {

  private enum Declaration implements FirstInterface, SecondInterface {
    FIRST_CONSTANT,
    SECOND_CONSTANT;
  }

  private interface FirstInterface {
  }

  private interface SecondInterface {
  }

}