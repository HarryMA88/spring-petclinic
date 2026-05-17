/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller responsible for handling all HTTP requests related to
 * {@link Pet} management within the context of a specific {@link Owner}. Exposes
 * endpoints for adding and editing pets, scoped under the
 * {@code /owners/{ownerId}} path.
 *
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Wick Dynex
 */
@Controller
@RequestMapping("/owners/{ownerId}")
class PetController {

	private static final String VIEWS_PETS_CREATE_OR_UPDATE_FORM = "pets/createOrUpdatePetForm";

	private final OwnerRepository owners;

	private final PetTypeRepository types;

	public PetController(OwnerRepository owners, PetTypeRepository types) {
		this.owners = owners;
		this.types = types;
	}

	/**
	 * Populates the model with all available {@link PetType} options, used to
	 * populate the pet type drop-down on the create/update form.
	 * @return a collection of all {@link PetType} instances
	 */
	@ModelAttribute("types")
	public Collection<PetType> populatePetTypes() {
		return this.types.findPetTypes();
	}

	/**
	 * Resolves the {@link Owner} model attribute for the current request using the
	 * {@code ownerId} path variable.
	 * @param ownerId the primary key of the owner
	 * @return the matching {@link Owner}
	 * @throws IllegalArgumentException if no owner exists with the given id
	 */
	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable("ownerId") int ownerId) {
		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
		return owner;
	}

	/**
	 * Resolves the {@link Pet} model attribute for the current request. Returns a
	 * new empty {@link Pet} when no {@code petId} path variable is present (i.e.
	 * during creation), or retrieves the existing pet from the owner.
	 * @param ownerId the primary key of the owner
	 * @param petId the primary key of the pet, or {@code null} for new-pet flows
	 * @return the existing {@link Pet} or a new empty instance
	 * @throws IllegalArgumentException if no owner exists with the given id
	 */
	@ModelAttribute("pet")
	public Pet findPet(@PathVariable("ownerId") int ownerId,
			@PathVariable(name = "petId", required = false) Integer petId) {

		if (petId == null) {
			return new Pet();
		}

		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
		return owner.getPet(petId);
	}

	/**
	 * Prevents the {@code id} field on the owner from being set via form binding,
	 * guarding against mass-assignment vulnerabilities.
	 * @param dataBinder the data binder to configure for the owner model attribute
	 */
	@InitBinder("owner")
	public void initOwnerBinder(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id", "*.id");
	}

	/**
	 * Registers {@link PetValidator} as the validator for the pet form and
	 * prevents the {@code id} field on the pet from being set via form binding,
	 * guarding against mass-assignment vulnerabilities.
	 * @param dataBinder the data binder to configure for the pet model attribute
	 */
	@InitBinder("pet")
	public void initPetBinder(WebDataBinder dataBinder) {
		dataBinder.setValidator(new PetValidator());
		dataBinder.setDisallowedFields("id", "*.id");
	}

	/**
	 * Displays the form for adding a new pet to the given owner.
	 * @param owner the owner to whom the new pet will belong
	 * @param model the Spring MVC model map
	 * @return the logical view name for the create/update pet form
	 */
	@GetMapping("/pets/new")
	public String initCreationForm(Owner owner, ModelMap model) {
		Pet pet = new Pet();
		owner.addPet(pet);
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	/**
	 * Processes the new-pet form submission. Validates that the pet name is not a
	 * duplicate for the owner, that the birth date is not in the future, and saves
	 * the pet on success.
	 * @param owner the owner to whom the new pet belongs
	 * @param pet the pet instance populated from the submitted form
	 * @param result holds any validation errors produced by {@link PetValidator}
	 * or bean validation
	 * @param redirectAttributes used to pass a success flash message after redirect
	 * @return a redirect to the owner's detail page on success, or the form view
	 * on validation failure
	 */
	@PostMapping("/pets/new")
	public String processCreationForm(Owner owner, @Valid Pet pet, BindingResult result,
			RedirectAttributes redirectAttributes) {

		if (StringUtils.hasText(pet.getName()) && pet.isNew() && owner.getPet(pet.getName(), true) != null) {
			result.rejectValue("name", "duplicate", "already exists");
		}

		LocalDate currentDate = LocalDate.now();
		if (pet.getBirthDate() != null && pet.getBirthDate().isAfter(currentDate)) {
			result.rejectValue("birthDate", "typeMismatch.birthDate");
		}

		if (result.hasErrors()) {
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		owner.addPet(pet);
		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "New Pet has been Added");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Displays the form for editing an existing pet. The pet is pre-populated via
	 * the {@link #findPet} model attribute method.
	 * @return the logical view name for the create/update pet form
	 */
	@GetMapping("/pets/{petId}/edit")
	public String initUpdateForm() {
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	/**
	 * Processes the pet update form submission. Validates that the updated name
	 * does not conflict with another of the owner's pets, that the birth date is
	 * not in the future, and delegates to {@link #updatePetDetails} on success.
	 * @param owner the owner of the pet being updated
	 * @param pet the pet instance populated from the submitted form
	 * @param result holds any validation errors produced by {@link PetValidator}
	 * or bean validation
	 * @param redirectAttributes used to pass a success flash message after redirect
	 * @return a redirect to the owner's detail page on success, or the form view
	 * on validation failure
	 */
	@PostMapping("/pets/{petId}/edit")
	public String processUpdateForm(Owner owner, @Valid Pet pet, BindingResult result,
			RedirectAttributes redirectAttributes) {

		String petName = pet.getName();

		// checking if the pet name already exists for the owner
		if (StringUtils.hasText(petName)) {
			Pet existingPet = owner.getPet(petName, false);
			if (existingPet != null && !Objects.equals(existingPet.getId(), pet.getId())) {
				result.rejectValue("name", "duplicate", "already exists");
			}
		}

		LocalDate currentDate = LocalDate.now();
		if (pet.getBirthDate() != null && pet.getBirthDate().isAfter(currentDate)) {
			result.rejectValue("birthDate", "typeMismatch.birthDate");
		}

		if (result.hasErrors()) {
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		updatePetDetails(owner, pet);
		redirectAttributes.addFlashAttribute("message", "Pet details has been edited");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Updates the details of an existing pet belonging to the owner, or adds the
	 * pet to the owner if it does not already exist. Persists the owner (and
	 * cascaded pets) to the repository.
	 * @param owner the owner of the pet
	 * @param pet the pet carrying the updated field values
	 */
	private void updatePetDetails(Owner owner, Pet pet) {
		Integer id = pet.getId();
		Assert.state(id != null, "'pet.getId()' must not be null");
		Pet existingPet = owner.getPet(id);
		if (existingPet != null) {
			// Update existing pet's properties
			existingPet.setName(pet.getName());
			existingPet.setBirthDate(pet.getBirthDate());
			existingPet.setType(pet.getType());
		}
		else {
			owner.addPet(pet);
		}
		this.owners.save(owner);
	}

}