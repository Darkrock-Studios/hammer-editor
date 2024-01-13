package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SpacerM() = Spacer(modifier = Modifier.size(Ui.Padding.M))

@Composable
fun SpacerL() = Spacer(modifier = Modifier.size(Ui.Padding.L))

@Composable
fun SpacerXL() = Spacer(modifier = Modifier.size(Ui.Padding.XL))
