package com.example.app.features

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.example.app.viewmodel.FarmHelpViewModel

@Composable
fun FarmHelp(viewModel: FarmHelpViewModel, onClose: (() -> Unit)? = null) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Image picker - Gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.setSelectedImageUri(uri)
        if (uri != null) viewModel.nextStep()
    }

    // Image picker - Camera
    val cameraImageUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            viewModel.setSelectedImageUri(cameraImageUri.value)
            if (cameraImageUri.value != null) viewModel.nextStep()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = viewModel.createImageUri(context)
            cameraImageUri.value = uri
            cameraLauncher.launch(uri)
        } else {
            viewModel.setErrorMessage("Camera permission is required to take a photo.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepIndicator(currentStep = uiState.currentStep, totalSteps = 3)
            if (onClose != null) {
                TextButton(onClick = onClose) {
                    Text("Close")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        AnimatedContent(targetState = uiState.currentStep, label = "Step Animation") { step ->
            when (step) {
                1 -> UploadStep(
                    onGallery = { galleryLauncher.launch("image/*") },
                    onCamera = {
                        val cameraGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED

                        if (cameraGranted) {
                            val uri = viewModel.createImageUri(context)
                            cameraImageUri.value = uri
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
                2 -> DescribeStep(
                    description = uiState.description,
                    selectedImageUri = uiState.selectedImageUri,
                    onDescriptionChange = { viewModel.setDescription(it) },
                    onSubmit = { viewModel.showConfirmationDialog(true) },
                    onBack = { viewModel.prevStep() }
                )
                3 -> SuccessStep(onHome = {
                    viewModel.reset()
                    onClose?.invoke()
                })
            }
        }

        if (uiState.showConfirmation) {
            ConfirmationDialog(
                onConfirm = { viewModel.submitPost(context) },
                onDismiss = { viewModel.showConfirmationDialog(false) }
            )
        }
        uiState.errorMessage?.let {
            Text(it, color = Color.Red, modifier = Modifier.padding(8.dp))
        }
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .padding(4.dp)
                    .background(
                        if (index + 1 <= currentStep) MaterialTheme.colorScheme.primary
                        else Color.Gray,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun UploadStep(onGallery: () -> Unit, onCamera: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "What Challenges are you facing on your Farm?",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Take a photo or upload a photo of the issue, and our certified officers will help.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onCamera,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Take Photo")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Take a Photo")
            }
            Button(
                onClick = onGallery,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Image, contentDescription = "Upload from Gallery")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload from Gallery")
            }
        }
    }
}

@Composable
fun DescribeStep(
    description: String,
    selectedImageUri: Uri?,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Explain The Photo",
            style = MaterialTheme.typography.headlineSmall
        )
        selectedImageUri?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = "Preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color.LightGray),
                contentScale = ContentScale.Crop
            )
        }
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            placeholder = { Text("Describe your issue...") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 5,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) { Text("Back") }
            Button(
                onClick = onSubmit,
                modifier = Modifier.weight(1f),
                enabled = description.isNotBlank()
            ) {
                Icon(Icons.Default.Check, contentDescription = "Submit")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Submit")
            }
        }
    }
}

@Composable
fun ConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Is this Information correct?") },
        text = { Text("Are you sure you want to submit?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Yes") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("No") }
        }
    )
}

@Composable
fun SuccessStep(onHome: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Success",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Submission Successful!",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your farm issue has been sent to a certified extension officer. Expect feedback soon.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onHome) {
            Text("Go Back")
        }
    }
}