package mycelium.core

trait Writer[Type, PickleType] {
  def write(msg: Type): PickleType
}

trait Reader[Type, PickleType] {
  def read(bm: PickleType): Either[Throwable, Type]
}
