package me.lyh.parquet.types

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable
import scala.reflect.ClassTag

trait FactoryCompat[-A, +C] extends Serializable { self =>
  def newBuilder: mutable.Builder[A, C]
  def build(xs: TraversableOnce[A]): C = (newBuilder ++= xs).result()
}

object FactoryCompat extends LowPriorityFactoryCompat1 {
  private type FC[A, C] = FactoryCompat[A, C]

  def apply[A, C](f: () => mutable.Builder[A, C]): FC[A, C] =
    new FactoryCompat[A, C] {
      override def newBuilder: mutable.Builder[A, C] = f()
    }

  implicit def arrayFC[A: ClassTag] = FactoryCompat(() => Array.newBuilder[A])
  // Deprecated in 2.13
  // implicit def traversableFC[A] = FactoryCompat(() => Traversable.newBuilder[A])
  // List <: Iterable
  // implicit def iterableFC[A] = FactoryCompat(() => Iterable.newBuilder[A])
  // List <: Seq
  // implicit def seqFC[A] = FactoryCompat(() => Seq.newBuilder[A])
  // Vector <: IndexedSeq
  // implicit def indexedSeqFC[A] = FactoryCompat(() => IndexedSeq.newBuilder[A])
}

trait LowPriorityFactoryCompat1 extends LowPriorityFactoryCompat2 {
  implicit def listFC[A] = FactoryCompat(() => List.newBuilder[A])
}

trait LowPriorityFactoryCompat2 {
  implicit def vectorFC[A] = FactoryCompat(() => Vector.newBuilder[A])
  // Deprecated in 2.13
  // implicit def streamFC[A] = FactoryCompat(() => Stream.newBuilder[A])
}
