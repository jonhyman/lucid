package com.stackmob.lucid

/**
 * Copyright 2012 StackMob
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import scalaz._
import Scalaz._
import ValidationT._

sealed trait ValidationT[F[_], E, A] {

  def run: F[Validation[E, A]]

  def map[B](f: A => B)(implicit F: Functor[F]): ValidationT[F, E, B] = {
    validationT(F.fmap(run, (_: Validation[E, A]).map(f)))
  }

  def flatMap[B](f: A => ValidationT[F, E, B])(implicit B: Bind[F], P: Pure[F]): ValidationT[F, E, B] = {
    validationT(B.bind(run, (_: Validation[E,A]).fold(failure = e => P.pure(e.fail[B]), success = a => f(a).run)))
  }

  def flatMapF[B](f: A => F[B])(implicit B: Bind[F], F: Functor[F], P: Pure[F]): ValidationT[F, E, B] = {
    validationT(B.bind(run, (_: Validation[E,A]).fold(
      failure = e => P.pure(e.fail[B]),
      success = v => F.fmap(f(v), (_: B).success[E])
    )))
  }

  def flatMapV[B](f: A => Validation[E, B])(implicit B: Bind[F], P: Pure[F]): ValidationT[F, E, B] = {
    validationT(B.bind(run, (_: Validation[E,A]).fold(
      failure = e => P.pure(e.fail[B]),
      success = v => P.pure(f(v))
    )))
  }

}

object ValidationT {

  def validationT[F[_], E, A](a: F[Validation[E, A]]): ValidationT[F, E, A] = new ValidationT[F, E, A] {
    override val run = a
  }

  implicit def ValidationTPure[F[_]: Pure, E]: Pure[({type VT[X] = ValidationT[F, E, X]})#VT] = new Pure[({type VT[X] = ValidationT[F, E, X]})#VT] {
    def pure[A](a: => A): ValidationT[F, E, A] = validationT(implicitly[Pure[F]].pure(Success(a)))
  }

  implicit def ValidationTFunctor[F[_]: Functor, E]: Functor[({type VT[X] = ValidationT[F, E, X]})#VT] = new Functor[({type VT[X] = ValidationT[F, E, X]})#VT] {
    def fmap[A,B](fa: ValidationT[F, E, A], f: A => B): ValidationT[F, E, B] = fa.map(f)
  }

  implicit def ValidationTBind[F[_], E](implicit F: Bind[F], P: Pure[F]): Bind[({type VT[X] = ValidationT[F, E, X]})#VT] = new Bind[({type VT[X] = ValidationT[F, E, X]})#VT] {
    def bind[A, B](fa: ValidationT[F, E, A], f: A => ValidationT[F, E, B]): ValidationT[F, E, B] = fa.flatMap(f)
  }

}
